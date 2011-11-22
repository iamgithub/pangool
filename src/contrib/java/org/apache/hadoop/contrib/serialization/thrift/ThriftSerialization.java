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
package org.apache.hadoop.contrib.serialization.thrift;

import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.Serialization;
import org.apache.hadoop.io.serializer.Serializer;
import org.apache.thrift.TBase;

/**
 * A {@link Serialization} for types generated by
 * <a href="http://incubator.apache.org/thrift/">Apache Thrift</a>.
 * Thrift types all descend from <code>com.facebook.thrift.TBase</code>.
 * <p>
 * To use this serialization, make sure that the Hadoop property
 * <code>io.serializations</code> includes the fully-qualified classname of this
 * class: <code>org.apache.hadoop.contrib.serialization.thrift.ThriftSerialization</code>.
 */
@SuppressWarnings("rawtypes")
public class ThriftSerialization implements Serialization<TBase> {
	
	private Map<Class<TBase>,Deserializer<TBase>> deserCache = new HashMap<Class<TBase>,Deserializer<TBase>>();
	//private Map<Class<TBase>,Serializer<TBase>> serCache = new HashMap<Class<TBase>,Serializer<TBase>>();
	private Serializer<TBase> ser;
	
	
  public boolean accept(Class<?> c) {
    return TBase.class.isAssignableFrom(c);
  }

  public Deserializer<TBase> getDeserializer(Class<TBase> c) {
  	Deserializer<TBase> deser = deserCache.get(c);
  	if (deser == null){
  		deser = new ThriftDeserializer<TBase>(c);
  		deserCache.put(c,deser);
  	}
    return deser;
  }

  public Serializer<TBase> getSerializer(Class<TBase> c) {
  	if (ser == null){
     ser =  new ThriftSerializer();
  	}
    return ser;
  }
}
