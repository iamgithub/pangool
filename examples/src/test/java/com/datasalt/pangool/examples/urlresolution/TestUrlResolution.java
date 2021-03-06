/**
 * Copyright [2012] [Datasalt Systems S.L.]
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
package com.datasalt.pangool.examples.urlresolution;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.hadoop.util.ToolRunner;
import org.junit.Test;

import com.datasalt.pangool.utils.test.AbstractHadoopTestLibrary;
import com.google.common.io.Files;

public class TestUrlResolution extends AbstractHadoopTestLibrary {

	private final static String TEST_FILE_URL_MAP = "src/test/resources/urlresolution/url-map.txt";
	private final static String TEST_FILE_URL_REG = "src/test/resources/urlresolution/url-reg.txt";
	private final static String EXPECTED_OUTPUT   = "src/test/resources/urlresolution/expected-output.txt";
	
	private final static String OUT_PANGOOL = "out-cogroup-pangool-co";
	
	@Test
	public void testPangool() throws Exception {
		ToolRunner.run(getConf(),new UrlResolution(),new String[] { TEST_FILE_URL_MAP, TEST_FILE_URL_REG, OUT_PANGOOL });
		String outPangool = getReducerOutputAsText(OUT_PANGOOL);
		String expectedOutput = getOutputAsText(EXPECTED_OUTPUT);
		assertEquals(expectedOutput,outPangool);
		trash(OUT_PANGOOL);
	}
	
	public String getReducerOutputAsText(String outputDir) throws IOException {
		return getOutputAsText(outputDir + "/part-r-00000");
	}
	
	public String getOutputAsText(String outFile) throws IOException {
		return Files.toString(new File(outFile), Charset.forName("UTF-8"));
	}
}
