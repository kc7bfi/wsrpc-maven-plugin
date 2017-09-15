package net.psgglobal.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.ToolManager;

import com.codesnippets4all.json.parsers.JSONParser;
import com.codesnippets4all.json.parsers.JsonParserFactory;

/*
This file is part of wsrpc.

wsrpc is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

wsrpc is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with wsrpc.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * The plugin class
 */
@Mojo(name = "generate")
@Execute(phase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateWsRpcMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.basedir}/src/main/resources/wsrpc", property = "inputDir", required = true)
	private File inputDir;

	@Parameter(defaultValue = "${project.build.directory}/generated-sources/wsrpc", property = "outputDir", required = true)
	private File outputDir;

	@Parameter(defaultValue = "${project}")
	private MavenProject project;

	/**
	 * Execute the plugin
	 * @throws MojoExecutionException any errors
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void execute() throws MojoExecutionException {

		// process each specification file
		if (inputDir == null) throw new MojoExecutionException("Cannot find inputDir");
		if (!inputDir.exists()) throw new MojoExecutionException("inputDir does not exist");
		for (File specificationFile : inputDir.listFiles()) {

			// read the specification file
			BufferedReader reader = null;
			String specificationSource = null;
			try {
				reader = new BufferedReader(new FileReader(specificationFile));
				specificationSource = readAsString(reader);
			} catch (IOException e) {
				getLog().warn("Error reading input sorce files: " + e.getMessage());
				throw new MojoExecutionException("Error reading input sorce files", e);
			} finally {
				if (reader != null) try { reader.close(); } catch (Exception e) { getLog().warn("Could not close reader " + e.getMessage()); }
			}
			JSONParser parser = JsonParserFactory.getInstance().newJsonParser();
			Map<String, Object> specification = null;
			try {
				specification = parser.parseJson(specificationSource);
			} catch (Exception e) {
				int i0 = e.getMessage().indexOf("Position::");
				if (i0 > 0) {
					int at = Integer.parseInt(e.getMessage().substring(i0 + "Position::".length()));
					getLog().info("JSON error at " + specificationSource.substring(at));
				}
				throw new MojoExecutionException("Cannot parse specification file", e);
			}
			String specName = (String) specification.get("name");
			String specPackage = (String) specification.get("package");

			vmGenerateConstsFiles(specName, specPackage, specification);
			vmGenerateClassFiles(specName, specPackage, specification);
			vmGenerateListFiles(specName, specPackage, specification);
			vmGenerateNoticeFiles(specName, specPackage, specification);
			vmGenerateRequestFiles(specName, specPackage, specification);
			vmGenerateActorFile(specName, specPackage, specification);
			vmGenerateReactorFile(specName, specPackage, specification);
			vmGenerateAgentFile(specName, specPackage, specification);

			project.addCompileSourceRoot(outputDir.getAbsolutePath());
		}
	}

	/**
	 * Read an entire resource as a string
	 * @param reader the buffered reader
	 * @return the string
	 * @throws IOException any errors
	 */
	private String readAsString(BufferedReader reader) throws IOException {
		StringBuilder str = new StringBuilder();
		for (String ln = reader.readLine(); ln != null; ln = reader.readLine()) str.append(ln + "\n");
		return str.toString();
	}

	/**
	 * Capitalize a string
	 * @param name the name
	 * @return the name capitalized
	 */
	private String capitalize(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	/**
	 * Create the directories for the generated files
	 * @param specName the specification name
	 * @param specPackage the package
	 * @return the package path
	 */
	private String createPackageDirs(String specName, String specPackage) {
		StringBuilder pathName = new StringBuilder(outputDir.getAbsolutePath() + "/");
		String[] subdirNames = specPackage.split("\\.");
		for (String subdirName : subdirNames) pathName.append(subdirName + "/");
		File pathFile = new File(pathName.toString());
		pathFile.mkdirs();
		return pathName.toString();
	}

	/**
	 * Generate all the classes files
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException Errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateClassFiles(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {
		List<Map<String, Object>> claszs = (List<Map<String, Object>>) specification.get("classes");
		if (claszs != null) {
			for (Map<String, Object> clasz : claszs) {
				vmGenerateClassFile(specName, specPackage, clasz);
			}
		}
	}

	/**
	 * Generate a single class file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param classSpecification the class specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateClassFile(String specName, String specPackage, Map<String, Object> classSpecification) throws MojoExecutionException {

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/ClassTemplate.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("className", classSpecification.get("name"));
		velocityContext.put("classJavadoc", classSpecification.get("javadoc"));
		velocityContext.put("members", getParametersMap((List<Map<String, Object>>) classSpecification.get("members")));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + capitalize((String) classSpecification.get("name")) + ".java";
		File claszCodeFile = new File(sourceCodePath);
		try {
			claszCodeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(claszCodeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate all the constants files
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException Errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateConstsFiles(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {
		List<Map<String, Object>> claszs = (List<Map<String, Object>>) specification.get("consts");
		if (claszs != null) {
			for (Map<String, Object> clasz : claszs) {
				vmGenerateConstsFile(specName, specPackage, clasz);
			}
		}
	}

	/**
	 * Generate a single constants file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param constSpecification the constant specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateConstsFile(String specName, String specPackage, Map<String, Object> constSpecification) throws MojoExecutionException {

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/ConstsTemplate.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("className", constSpecification.get("name"));
		velocityContext.put("classJavadoc", constSpecification.get("javadoc"));
		velocityContext.put("members", getParametersMap((List<Map<String, Object>>) constSpecification.get("members")));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + capitalize((String) constSpecification.get("name")) + ".java";
		File claszCodeFile = new File(sourceCodePath);
		try {
			claszCodeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(claszCodeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate all the list files
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException Errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateListFiles(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {
		List<Map<String, Object>> lists = (List<Map<String, Object>>) specification.get("lists");
		if (lists != null) {
			for (Map<String, Object> list : lists) {
				vmGenerateListFile(specName, specPackage, list);
			}
		}
	}

	/**
	 * Generate a single list file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param listSpecification the list specification
	 * @throws MojoExecutionException errors
	 */
	private void vmGenerateListFile(String specName, String specPackage, Map<String, Object> listSpecification) throws MojoExecutionException {

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/ListTemplate.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("listName", listSpecification.get("name"));
		velocityContext.put("listOf", listSpecification.get("listOf"));
		velocityContext.put("listJavadoc", listSpecification.get("javadoc"));
		velocityContext.put("item", listSpecification.get("item"));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + capitalize((String) listSpecification.get("name")) + ".java";
		File listCodeFile = new File(sourceCodePath);
		try {
			listCodeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(listCodeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate all the notice files
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException Errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateNoticeFiles(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {
		List<Map<String, Object>> notices = (List<Map<String, Object>>) specification.get("notices");
		if (notices != null) {
			for (Map<String, Object> notice : notices) {
				vmGenerateNoticeFile(specName, specPackage, notice);
			}
		}
	}

	/**
	 * Generate a single notice file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param noticeSpecification the notice specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateNoticeFile(String specName, String specPackage, Map<String, Object> noticeSpecification) throws MojoExecutionException {

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/NoticeTemplate.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("noticeName", noticeSpecification.get("name"));
		velocityContext.put("noticeJavadoc", noticeSpecification.get("javadoc"));
		velocityContext.put("parameters", getParametersMap((List<Map<String, Object>>) noticeSpecification.get("parameters")));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + capitalize((String) noticeSpecification.get("name")) + "Notice.java";
		File noticeCodeFile = new File(sourceCodePath);
		try {
			noticeCodeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(noticeCodeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate all the request files
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException Errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateRequestFiles(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {
		List<Map<String, Object>> requests = (List<Map<String, Object>>) specification.get("requests");
		for (Map<String, Object> request : requests) {
			vmGenerateRequestFile(specName, specPackage, request);
		}
	}

	/**
	 * Generate a single request file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param requestSpecification the request specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateRequestFile(String specName, String specPackage, Map<String, Object> requestSpecification) throws MojoExecutionException {

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/RequestTemplate.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("requestName", requestSpecification.get("name"));
		velocityContext.put("requestJavadoc", requestSpecification.get("javadoc"));
		velocityContext.put("parameters", getParametersMap((List<Map<String, Object>>) requestSpecification.get("parameters")));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + capitalize((String) requestSpecification.get("name")) + "Request.java";
		File requestCodeFile = new File(sourceCodePath);
		try {
			requestCodeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(requestCodeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate The reactor file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateReactorFile(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {

		boolean supportBinaryData = "true".equalsIgnoreCase((String) specification.get("supportBinaryData"));

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/Reactor" + (supportBinaryData ? "Binary" : "Text") + "Template.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("specname", specification.get("name"));
		velocityContext.put("rquireAuth", specification.get("jwtSecurity") == null || "true".equals(specification.get("jwtSecurity")));
		velocityContext.put("synchronized", specification.getOrDefault("synchronized", "none"));
		velocityContext.put("clientRequests", getClientRequests((List<Map<String, Object>>) specification.get("requests"), specification));
		velocityContext.put("clientNotices", getClientRequests((List<Map<String, Object>>) specification.get("notices"), specification));
		velocityContext.put("serverRequests", getServerRequests((List<Map<String, Object>>) specification.get("requests"), specification));
		velocityContext.put("serverNotices", getServerRequests((List<Map<String, Object>>) specification.get("notices"), specification));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + specification.get("name") + "Reactor.java";
		File codeFile = new File(sourceCodePath);
		try {
			codeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(codeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate the Actor file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateActorFile(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {

		boolean supportBinaryData = "true".equalsIgnoreCase((String) specification.get("supportBinaryData"));

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/Actor" + (supportBinaryData ? "Binary" : "Text") + "Template.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("specname", specification.get("name"));
		velocityContext.put("synchronized", specification.getOrDefault("synchronized", "none"));
		velocityContext.put("clientRequests", getClientRequests((List<Map<String, Object>>) specification.get("requests"), specification));
		velocityContext.put("clientNotices", getClientRequests((List<Map<String, Object>>) specification.get("notices"), specification));
		velocityContext.put("serverRequests", getServerRequests((List<Map<String, Object>>) specification.get("requests"), specification));
		velocityContext.put("serverNotices", getServerRequests((List<Map<String, Object>>) specification.get("notices"), specification));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + specification.get("name") + "Actor.java";
		File codeFile = new File(sourceCodePath);
		try {
			codeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(codeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Generate the Agent file
	 * @param specName the specification name
	 * @param specPackage the specification package
	 * @param specification the specification
	 * @throws MojoExecutionException errors
	 */
	@SuppressWarnings("unchecked")
	private void vmGenerateAgentFile(String specName, String specPackage, Map<String, Object> specification) throws MojoExecutionException {

		boolean supportBinaryData = "true".equalsIgnoreCase((String) specification.get("supportBinaryData"));

		// initialize the Velocity engine
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		velocityEngine.init();
		Template vilocityTemplate = velocityEngine.getTemplate("templates/Agent" + (supportBinaryData ? "Binary" : "Text") + "Template.vm");

		// use standard tools
		Map<String, Object> toolProperties = new HashMap<String, Object>();
		toolProperties.put("engine", velocityEngine);
		ToolManager toolManager = new ToolManager(true, true);

		// set up the Velocity context model
		ToolContext velocityContext = toolManager.createContext();
		velocityContext.put("packageName", specPackage);
		velocityContext.put("specname", specification.get("name"));
		velocityContext.put("clientRequests", getClientRequests((List<Map<String, Object>>) specification.get("requests"), specification));
		velocityContext.put("clientNotices", getClientRequests((List<Map<String, Object>>) specification.get("notices"), specification));
		velocityContext.put("serverRequests", getServerRequests((List<Map<String, Object>>) specification.get("requests"), specification));
		velocityContext.put("serverNotices", getServerRequests((List<Map<String, Object>>) specification.get("notices"), specification));

		// generate the code
		StringWriter codeWriter = new StringWriter();
		vilocityTemplate.merge(velocityContext, codeWriter);

		// save the code
		String dirPath = createPackageDirs(specName, specPackage);
		String sourceCodePath = dirPath + specification.get("name") + "Agent.java";
		File codeFile = new File(sourceCodePath);
		try {
			codeFile.createNewFile();
		} catch (IOException e) {
			getLog().warn("Cannot create source code file " + sourceCodePath);
			throw new MojoExecutionException("Cannot create source code file");
		}
		FileWriter writer = null;
		try {
			writer = new FileWriter(codeFile);
			writer.write(codeWriter.toString());
			getLog().info("Wrote " + sourceCodePath);
		} catch (IOException e) {
			getLog().warn("Cannot write source code file " + sourceCodePath + ": " + e.getMessage());
			throw new MojoExecutionException("Cannot write source code file");
		} finally {
			if (writer != null) try { writer.close(); } catch (Exception e) { getLog().warn("Could not close writer: " + e.getMessage()); }
		}
	}

	/**
	 * Gather the parameter/members elements
	 * @param paramatersSpec the parameter specification
	 * @return the parameters map
	 */
	private List<Map<String, String>> getParametersMap(List<Map<String, Object>> paramatersSpec) {
		List<Map<String, String>> parameters = new LinkedList<Map<String, String>>();
		if (paramatersSpec != null) {
			for (Map<String, Object> paramaterSpec : paramatersSpec) {
				Map<String, String> parameter = new HashMap<String, String>();
				parameter.put("name", (String) paramaterSpec.get("name"));
				parameter.put("type", (String) paramaterSpec.get("type"));
				parameter.put("value", (String) paramaterSpec.get("value"));
				parameter.put("javadoc", (String) paramaterSpec.get("javadoc"));
				parameters.add(parameter);
			}
		}
		return parameters;
	}

	/**
	 * Gather the client requests
	 * @param requestsSpec the request spec
	 * @param specification the specification
	 * @return the client requests
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> getClientRequests(List<Map<String, Object>> requestsSpec, Map<String, Object> specification) {
		List<Map<String, Object>> clientRequests = new LinkedList<Map<String, Object>>();
		if (requestsSpec != null) {
			for (Map<String, Object> requestSpec : requestsSpec) {
				Map<String, Object> request = new HashMap<String, Object>();
				request.put("name", requestSpec.get("name"));
				request.put("returns", requestSpec.get("returns"));
				request.put("returnsJavadoc", requestSpec.get("returnsJavadoc"));
				request.put("parameters", getParametersMap((List<Map<String, Object>>) requestSpec.get("parameters")));
				String defTimeout = specification.get("defaultTimeout") == null ? "200" : (String) specification.get("defaultTimeout");
				request.put("defTimeout", defTimeout);
				if ("true".equals(requestSpec.get("abstractOnCall"))) request.put("abstractOnCall", "true");
				if ("client".equals(requestSpec.get("sender")) || "both".equals(requestSpec.get("sender"))) {
					clientRequests.add(request);
				}
			}
		}
		return clientRequests;
	}

	/**
	 * Gather the server requests
	 * @param requestsSpec the request spec
	 * @param specification the specification
	 * @return the server requests
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> getServerRequests(List<Map<String, Object>> requestsSpec, Map<String, Object> specification) {
		List<Map<String, Object>> serverRequests = new LinkedList<Map<String, Object>>();
		if (requestsSpec != null) {
			for (Map<String, Object> requestSpec : requestsSpec) {
				Map<String, Object> request = new HashMap<String, Object>();
				request.put("name", requestSpec.get("name"));
				request.put("returns", requestSpec.get("returns"));
				request.put("returnsJavadoc", requestSpec.get("returnsJavadoc"));
				request.put("parameters", getParametersMap((List<Map<String, Object>>) requestSpec.get("parameters")));
				String defTimeout = specification.get("defaultTimeout") == null ? "200" : (String) specification.get("defaultTimeout");
				request.put("defTimeout", defTimeout);
				if ("true".equals(requestSpec.get("abstractOnCall"))) request.put("abstractOnCall", "true");
				if ("server".equals(requestSpec.get("sender")) || "both".equals(requestSpec.get("sender"))) {
					serverRequests.add(request);
				}
			}
		}
		return serverRequests;
	}
}
