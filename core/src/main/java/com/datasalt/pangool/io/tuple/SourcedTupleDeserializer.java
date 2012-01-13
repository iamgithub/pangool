/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datasalt.pangool.io.tuple;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.VIntWritable;
import org.apache.hadoop.io.VLongWritable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.util.ReflectionUtils;

import com.datasalt.pangolin.commons.Buffer;

import com.datasalt.pangolin.io.Serialization;
import com.datasalt.pangool.PangoolConfig;
import com.datasalt.pangool.Schema;
import com.datasalt.pangool.Schema.Field;


class SourcedTupleDeserializer implements Deserializer<SourcedTuple> {

	private PangoolConfig pangoolConf;
	private DataInputStream in;
	private Text text = new Text();
	private Serialization ser;
	private Map<String,Enum<?>[]> cachedEnums = new HashMap<String,Enum<?>[]>();
	
	private Buffer tmpInputBuffer = new Buffer();
	//private 
	
	
	SourcedTupleDeserializer(Serialization ser,PangoolConfig pangoolConfig){
		this.pangoolConf = pangoolConfig;
		this.ser = ser;
		this.cachedEnums = SourcedTupleSerialization.getEnums(pangoolConfig);
	}
	
	@Override
	public void open(InputStream in) throws IOException {
		this.in = new DataInputStream(in);
	}

	@Override
	public SourcedTuple deserialize(SourcedTuple t) throws IOException {
		if (t == null) {
			t = ReflectionUtils.newInstance(SourcedTuple.class, null);
		}
		Schema commonSchema = pangoolConf.getCommonOrderedSchema();
		readFields(commonSchema,t,in);
		int numSchemas = pangoolConf.getSchemes().size();
		if (numSchemas > 1){
			// in this step source should be set 
			Schema specificSchema = pangoolConf.getSpecificOrderedSchema(t.getSource());
			readFields(specificSchema,t,in);
		}
		return t;
	}
	
	
	
	public void readFields(Schema schema,SourcedTuple tuple ,DataInput input) throws IOException {
		for (int i =0 ; i < schema.getFields().size(); i++) {
			Class<?> fieldType = schema.getField(i).getType();
			String fieldName = schema.getField(i).getName();
			if (Field.SOURCE_ID_FIELD_NAME.equals(fieldName)){
				tuple.setSource(WritableUtils.readVInt(input));
			} else if (fieldType == VIntWritable.class) {
				tuple.setInt(fieldName,WritableUtils.readVInt(input));
			} else if (fieldType == VLongWritable.class) {
				tuple.setLong(fieldName,WritableUtils.readVLong(input));
			} else if (fieldType == Integer.class){
				tuple.setInt(fieldName,input.readInt());
			} else if (fieldType == Long.class){
				tuple.setLong(fieldName, input.readLong());
			}	else if (fieldType == Double.class) {
				tuple.setDouble(fieldName, input.readDouble());
			} else if (fieldType == Float.class) {
				tuple.setFloat(fieldName, input.readFloat());
			} else if (fieldType == String.class) {
				text.readFields(input);
				tuple.setString(fieldName, text.toString());
			} else if (fieldType == Boolean.class) {
				byte b = input.readByte();
				tuple.setBoolean(fieldName, (b != 0));
			} else if (fieldType.isEnum()){
				int ordinal = WritableUtils.readVInt(input);
				try{
					Enum<?>[] enums = cachedEnums.get(fieldName);
					if (enums == null){
						throw new IOException("Field "+ fieldName + " is not a enum type");
					}
					tuple.setObject(fieldName,enums[ordinal]);
				} catch (ArrayIndexOutOfBoundsException e){
					throw new RuntimeException(e);
				}
			} else {
				int size =WritableUtils.readVInt(input);
				if (size != 0){
					tmpInputBuffer.setSize(size);
					input.readFully(tmpInputBuffer.getBytes(),0,size);
					if (tuple.getObject(fieldName) == null){
						tuple.setObject(fieldName,ReflectionUtils.newInstance(fieldType, null));
					}
					
					Object ob = ser.deser(tuple.getObject(fieldName),tmpInputBuffer.getBytes(),0,size);
					tuple.setObject(fieldName, ob);
					
				} else {
					tuple.setObject(fieldName, null);
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		in.close();
		
	}
}