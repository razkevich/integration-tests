package org.razkevich;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.teststeps.JdbcTestStepResult;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStepResult;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.support.PropertiesMap;
import com.eviware.soapui.model.testsuite.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SoapUITest {
	private static final String SOAPUI_TARGET_PATH = "target/test-classes/", propertiesFileName = SOAPUI_TARGET_PATH + "soap-ui.properties";
	public static final Comparator<File> FILE_COMPARATOR = new Comparator<File>() {
		@Override
		public int compare(File o1, File o2) {
			return o1.getName().compareTo(o2.getName());
		}
	};
	private static String filesInclude[], filesExclude[], releaseDir;
	private TestCase currentTestCase;
	private static final Logger log = LogManager.getLogger(SoapUITest.class.getName());

	public SoapUITest(TestCase currentTestCase) {
		this.currentTestCase = currentTestCase;
	}

	@Test
	public void runSoapUITest() {
		Assume.assumeFalse(currentTestCase.isDisabled());
		log.info(String.format("Running TestSuite '%s', TestCase '%s'", currentTestCase.getTestSuite().getName(), currentTestCase.getName()));
		checkStatus(logResultsAndReturn(currentTestCase.run(new PropertiesMap(), false)));
	}

	private void checkStatus(TestCaseRunner testRunner) {
		if (testRunner.getStatus() == TestRunner.Status.FAILED)
			Assert.fail(String.format("\nSoapUI Test Failed \nTestSuite: %s\nTestCase: %s\nReason: %s", currentTestCase.getTestSuite().getName(), currentTestCase.getName(), testRunner.getReason()));
	}

	private TestCaseRunner logResultsAndReturn(TestCaseRunner testRunner) {
		for (TestStepResult result : testRunner.getResults()) {
			final StringBuilder resultMessage = new StringBuilder(String.format("Step result %s -> %s -> %s: %s", result.getTestStep().getTestCase().getTestSuite().getName(), result.getTestStep().getTestCase().getName(), result.getTestStep().getName(), result.getStatus()));
			resultMessage.append(String.format("\nMessages:\n%s", ArrayUtils.isEmpty(result.getMessages()) ? "none" : StringUtils.join(result.getMessages(), '\n')));
			if (result instanceof WsdlTestRequestStepResult || result instanceof JdbcTestStepResult) {
				final MessageExchange message = (MessageExchange) result;
				resultMessage.append(String.format("\nEndPoint: %s\nRequest:\n%s\nResponse:\n%s", result instanceof WsdlTestRequestStepResult ? message.getEndpoint() : "-", result instanceof WsdlTestRequestStepResult ? message.getRequestContentAsXml() : message.getRequestContent(), message.getResponseContentAsXml()));
			}
			log.log((result.getStatus() == TestStepResult.TestStepStatus.OK || result.getStatus() == TestStepResult.TestStepStatus.UNKNOWN) ? Level.INFO : Level.ERROR, resultMessage);
		}
		return testRunner;
	}

	@Parameters
	public static Collection<TestCase> getParams() throws Exception {
		final Properties props = new Properties();
		final File file = new File(propertiesFileName);
		log.info("Include files: {}", ObjectUtils.defaultIfNull(filesInclude = StringUtils.split(System.getProperty("filesInclude"), ","), "none"));
		log.info("Exclude files: {}", ObjectUtils.defaultIfNull(filesExclude = StringUtils.split(System.getProperty("filesExclude"), ","), "none"));
		log.info("Configuration file: {}", propertiesFileName);
		new FileInputStream(file) {{
			props.load(this);
		}}.close();
		for (String key : props.stringPropertyNames()) {
			final String outerValue = System.getProperty(key);
			if (StringUtils.isNotBlank(outerValue)) {
				props.setProperty(key, outerValue);
				log.info("Property is overridden by the command line argument: {} -> {}", key, outerValue);
			}
		}
		final Collection<File> files = FileUtils.listFiles(new File(releaseDir = SOAPUI_TARGET_PATH + props.getProperty("release") + "/"), null, false);
		Collections.sort((List) files, FILE_COMPARATOR);
		log.info(files.isEmpty() ? "Release dir selected: {}" : "Release dir not found or empty:  {}", releaseDir);
		new FileOutputStream(file) {{
			props.store(this, "#Auto-generated properties file. Some parameters are replaced by the command line keys");
		}}.close();
		return new ArrayList<TestCase>() {{
			for (File f : ArrayUtils.isEmpty(filesInclude) ? files : getFilesFromStrings(SoapUITest.filesInclude)) {
				if (isFileOK(f.getName()))
					addAll(getTestCases(f.getName()));
			}
		}};
	}

	private static List<File> getFilesFromStrings(final String[] strings) {
		return new ArrayList<File>() {{
			for (String string : strings) {
				add(new File(string));
			}
		}};
	}

	private static boolean isFileOK(String file) {
		return StringUtils.isNotBlank(file)
				&& !new File(releaseDir + file).isDirectory()
				&& "xml".equals(FilenameUtils.getExtension(file))
				&& !ArrayUtils.contains(filesExclude, file);
	}

	private static Collection<TestCase> getTestCases(final String file) throws Exception {
		return new ArrayList<TestCase>() {{
			for (TestSuite ts : new WsdlProject(releaseDir + file).getTestSuiteList())
				addAll(ts.getTestCaseList());
		}};
	}
}
