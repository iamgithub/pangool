/**
 * Copyright [2011] [Datasalt Systems S.L.]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasalt.pangolin.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.SerializationFactory;
import org.apache.hadoop.io.serializer.Serializer;

/**
 * You can use this utility class to serialize / deserialize anything in the Hadoop context.
 * It is thread safe. Instanciate once, reuse many times. Otherwhise it is not
 * efficient.
 * 
 * @author pere,epalace
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Serialization {

	private SerializationFactory serialization;
	
	public Serialization(Configuration conf) throws IOException {
		serialization = new SerializationFactory(conf);
	}
	
	// only one byte array per thread, to make it thread safe and efficient
//	private ThreadLocal<DataOutputBuffer> cachedOutputStream = new ThreadLocal<DataOutputBuffer>() {
//
//		@Override
//    protected DataOutputBuffer initialValue() {
//			return new DataOutputBuffer();
//    }
//	};
	
	private ThreadLocal<DataInputBuffer> cachedInputStream = new ThreadLocal<DataInputBuffer>() {

		@Override
    protected DataInputBuffer initialValue() {
			return new DataInputBuffer();
    }
	};
	
	public int ser(Object datum,DataOutputBuffer output) throws IOException {
    Serializer ser = serialization.getSerializer(datum.getClass());
    //DataOutputBuffer baOs = cachedOutputStream.get();
    //output.wr
    //output.reset(); //THIS SHOULD BE DONE OUTSIDE
		ser.open(output);
		ser.serialize(datum);
		ser.close();
		return output.getLength();
		
	}
	
	public <T> T deser(Object obj, BytesWritable writable) throws IOException {
	  return (T)deser(obj,writable.getBytes(),0,writable.getLength());
	}
	
	public <T> T deser(Object obj, byte[] datum, int offset, int length) throws IOException {
	  Deserializer deSer = serialization.getDeserializer(obj.getClass());
		DataInputBuffer baIs = cachedInputStream.get();
		baIs.reset(datum, length);
		deSer.open(baIs);
		obj = deSer.deserialize(obj);
		deSer.close();
		baIs.close();
    return (T)obj;
	}
	
	public <T> T deser(Object obj,InputStream in) throws IOException {
		Deserializer deSer = serialization.getDeserializer(obj.getClass());
		deSer.open(in);
		obj = deSer.deserialize(obj);
		deSer.close();
		return (T)obj;
	}
	
}
