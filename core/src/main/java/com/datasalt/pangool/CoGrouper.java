package com.datasalt.pangool;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import com.datasalt.pangool.api.CombinerHandler;
import com.datasalt.pangool.api.GroupHandler;
import com.datasalt.pangool.api.GroupHandlerWithRollup;
import com.datasalt.pangool.api.InputProcessor;
import com.datasalt.pangool.api.ProxyOutputFormat;
import com.datasalt.pangool.commons.DCUtils;
import com.datasalt.pangool.io.AvroUtils;
import com.datasalt.pangool.io.PangoolMultipleOutputs;
import com.datasalt.pangool.io.TupleInputFormat;
import com.datasalt.pangool.io.TupleOutputFormat;
import com.datasalt.pangool.io.tuple.DoubleBufferedTuple;
import com.datasalt.pangool.io.tuple.ITuple;
import com.datasalt.pangool.io.tuple.ser.TupleInternalSerialization;
import com.datasalt.pangool.mapreduce.GroupComparator;
import com.datasalt.pangool.mapreduce.Partitioner;
import com.datasalt.pangool.mapreduce.RollupReducer;
import com.datasalt.pangool.mapreduce.SimpleCombiner;
import com.datasalt.pangool.mapreduce.SimpleReducer;
import com.datasalt.pangool.mapreduce.SortComparator;
import com.datasalt.pangool.mapreduce.lib.input.PangoolMultipleInputs;

@SuppressWarnings("rawtypes")
public class CoGrouper {

	private static final class Output {

		String name;
		Class<? extends OutputFormat> outputFormat;
		Class keyClass;
		Class valueClass;

		Map<String, String> specificContext = new HashMap<String, String>();

		Output(String name, Class<? extends OutputFormat> outputFormat, Class keyClass, Class valueClass,
		    Map<String, String> specificContext) {
			this.outputFormat = outputFormat;
			this.keyClass = keyClass;
			this.valueClass = valueClass;
			this.name = name;
			if(specificContext != null) {
				this.specificContext = specificContext;
			}
		}
	}

	private static final class Input {

		Path path;
		Class<? extends InputFormat> inputFormat;
		InputProcessor inputProcessor;

		Input(Path path, Class<? extends InputFormat> inputFormat, InputProcessor inputProcessor) {
			this.path = path;
			this.inputFormat = inputFormat;
			this.inputProcessor = inputProcessor;
		}
	}

	private Configuration conf;
	private CoGrouperConfig config;

	private GroupHandler grouperHandler;
	private CombinerHandler combinerHandler;
	private Class<? extends OutputFormat> outputFormat;
	private Class<?> jarByClass;
	private Class<?> outputKeyClass;
	private Class<?> outputValueClass;

	private Path outputPath;

	private List<Input> multiInputs = new ArrayList<Input>();
	private List<Output> namedOutputs = new ArrayList<Output>();

	public CoGrouper(CoGrouperConfig config, Configuration conf) {
		this.conf = conf;
		this.config = config;
	}

	// ------------------------------------------------------------------------- //

	public CoGrouper setJarByClass(Class<?> jarByClass) {
		this.jarByClass = jarByClass;
		return this;
	}

	public CoGrouper addTupleInput(Path path, InputProcessor<ITuple, NullWritable> inputProcessor) {
		this.multiInputs.add(new Input(path, TupleInputFormat.class, inputProcessor));
		AvroUtils.addAvroSerialization(conf);
		return this;
	}

	public CoGrouper addInput(Path path, Class<? extends InputFormat> inputFormat, InputProcessor inputProcessor) {
		this.multiInputs.add(new Input(path, inputFormat, inputProcessor));
		return this;
	}

	public CoGrouper setCombinerHandler(CombinerHandler combinerHandler) {
		this.combinerHandler = combinerHandler;
		return this;
	}

	public CoGrouper setOutput(Path outputPath, Class<? extends OutputFormat> outputFormat, Class<?> outputKeyClass,
	    Class<?> outputValueClass) {
		this.outputFormat = outputFormat;
		this.outputKeyClass = outputKeyClass;
		this.outputValueClass = outputValueClass;
		this.outputPath = outputPath;
		return this;
	}

	public CoGrouper setTupleOutput(Path outputPath, Schema schema) {
		this.outputPath = outputPath;
		this.outputFormat = TupleOutputFormat.class;
		this.outputKeyClass = ITuple.class;
		this.outputValueClass = NullWritable.class;
		conf.set(TupleOutputFormat.CONF_TUPLE_OUTPUT_SCHEMA, schema.toString());
		AvroUtils.addAvroSerialization(conf);
		return this;
	}

	public CoGrouper setGroupHandler(GroupHandler groupHandler) {
		this.grouperHandler = groupHandler;
		return this;
	}

	public CoGrouper addNamedOutput(String namedOutput, Class<? extends OutputFormat> outputFormatClass, Class keyClass,
	    Class valueClass) throws CoGrouperException {

		return addNamedOutput(namedOutput, outputFormatClass, keyClass, valueClass, null);
	}

	public CoGrouper addNamedOutput(String namedOutput, Class<? extends OutputFormat> outputFormatClass, Class keyClass,
	    Class valueClass, Map<String, String> specificContext) throws CoGrouperException {
		validateNamedOutput(namedOutput);
		namedOutputs.add(new Output(namedOutput, outputFormatClass, keyClass, valueClass, specificContext));
		return this;
	}

	public CoGrouper addNamedTupleOutput(String namedOutput, Schema outputSchema) throws CoGrouperException {
		validateNamedOutput(namedOutput);
		Map<String, String> specificContext = new HashMap<String, String>();
		specificContext.put(TupleOutputFormat.CONF_TUPLE_OUTPUT_SCHEMA, outputSchema.toString());
		Output output = new Output(namedOutput, TupleOutputFormat.class, ITuple.class, NullWritable.class, specificContext);
		AvroUtils.addAvroSerialization(conf);
		namedOutputs.add(output);
		return this;
	}

	private void validateNamedOutput(String namedOutput) throws CoGrouperException {
		PangoolMultipleOutputs.validateOutputName(namedOutput);
		for(Output existentNamedOutput : namedOutputs) {
			if(existentNamedOutput.name.equals(namedOutput)) {
				throw new CoGrouperException("Duplicate named output: " + namedOutput);
			}
		}
	}

	// ------------------------------------------------------------------------- //

	private void raiseExceptionIfNull(Object ob, String message) throws CoGrouperException {
		if(ob == null) {
			throw new CoGrouperException(message);
		}
	}

	private void raiseExceptionIfEmpty(Collection ob, String message) throws CoGrouperException {
		if(ob == null || ob.isEmpty()) {
			throw new CoGrouperException(message);
		}
	}

	public Job createJob() throws IOException, CoGrouperException {

		raiseExceptionIfNull(grouperHandler, "Need to set a group handler");
		raiseExceptionIfEmpty(multiInputs, "Need to add at least one input");
		raiseExceptionIfNull(outputFormat, "Need to set output format");
		raiseExceptionIfNull(outputKeyClass, "Need to set outputKeyClass");
		raiseExceptionIfNull(outputValueClass, "Need to set outputValueClass");
		raiseExceptionIfNull(outputPath, "Need to set outputPath");

		if(config.getRollupFrom() != null) {

			// Check that rollupFrom is contained in groupBy

			if(!config.getGroupByFields().contains(config.getRollupFrom())) {
				throw new CoGrouperException("Rollup from [" + config.getRollupFrom() + "] not contained in group by fields "
				    + config.getGroupByFields());
			}

			// Check that we are using the appropriate Handler

			if(!(grouperHandler instanceof GroupHandlerWithRollup)) {
				throw new CoGrouperException("Can't use " + grouperHandler + " with rollup. Please use "
				    + GroupHandlerWithRollup.class + " instead.");
			}
		}

		// Serialize PangoolConf in Hadoop Configuration
		CoGrouperConfig.setPangoolConfig(config, conf);
		Job job = new Job(conf);

		List<String> partitionerFields;

		if(config.getRollupFrom() != null) {
			// Grouper with rollup: calculate rollupBaseGroupFields from "rollupFrom"
			List<String> rollupBaseGroupFields = new ArrayList<String>();
			for(String groupByField : config.getGroupByFields()) {
				rollupBaseGroupFields.add(groupByField);
				if(groupByField.equals(config.getRollupFrom())) {
					break;
				}
			}
			partitionerFields = rollupBaseGroupFields;
			job.setReducerClass(RollupReducer.class);
		} else {
			// Simple grouper
			partitionerFields = config.getGroupByFields();
			job.setReducerClass(SimpleReducer.class);
		}

		// Set fields to partition by in Hadoop Configuration
		Partitioner.setPartitionerFields(job.getConfiguration(), partitionerFields);

		if(combinerHandler != null) {
			job.setCombinerClass(SimpleCombiner.class); // not rollup by now
			// Set Combiner Handler
			String uniqueName = UUID.randomUUID().toString() + '.' + "combiner-handler.dat";
			try {
				DCUtils.serializeToDC(combinerHandler, uniqueName, SimpleCombiner.CONF_COMBINER_HANDLER, job.getConfiguration());
			} catch(URISyntaxException e1) {
				throw new CoGrouperException(e1);
			}
		}

		// Set Group Handler
		try {
			String uniqueName = UUID.randomUUID().toString() + '.' + "group-handler.dat";
			DCUtils.serializeToDC(grouperHandler, uniqueName, SimpleReducer.CONF_REDUCER_HANDLER, job.getConfiguration());
		} catch(URISyntaxException e1) {
			throw new CoGrouperException(e1);
		}

		// Enabling serialization
		TupleInternalSerialization.enableSerialization(job.getConfiguration());

		job.setJarByClass((jarByClass != null) ? jarByClass : grouperHandler.getClass());
		job.setOutputFormatClass(outputFormat);
		job.setMapOutputKeyClass(DoubleBufferedTuple.class);
		job.setMapOutputValueClass(NullWritable.class);
		job.setPartitionerClass(Partitioner.class);
		job.setGroupingComparatorClass(GroupComparator.class);
		job.setSortComparatorClass(SortComparator.class);
		job.setOutputKeyClass(outputKeyClass);
		job.setOutputValueClass(outputValueClass);
		FileOutputFormat.setOutputPath(job, outputPath);
		for(Input input : multiInputs) {
			PangoolMultipleInputs.addInputPath(job, input.path, input.inputFormat, input.inputProcessor);
		}
		for(Output output : namedOutputs) {
			PangoolMultipleOutputs.addNamedOutput(job, output.name, output.outputFormat, output.keyClass, output.valueClass);
			for(Map.Entry<String, String> contextKeyValue : output.specificContext.entrySet()) {
				PangoolMultipleOutputs.addNamedOutputContext(job, output.name, contextKeyValue.getKey(),
				    contextKeyValue.getValue());
			}
		}
		if(namedOutputs.size() > 0) {
			// Configure a {@link ProxyOutputFormat} for Pangool's Multiple Outputs to work: {@link PangoolMultipleOutput}
			try {
				job.getConfiguration().setClass(ProxyOutputFormat.PROXIED_OUTPUT_FORMAT_CONF, job.getOutputFormatClass(),
				    OutputFormat.class);
			} catch(ClassNotFoundException e) {
				// / will never happen
			}
			job.setOutputFormatClass(ProxyOutputFormat.class);
		}
		return job;
	}
}