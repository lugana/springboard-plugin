package fr.wseduc.gradle.springboard

import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.process.ExecResult

class SpringboardPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		project.task("generateConf") << {
			FileUtils.createFile("conf.properties", "ent-core.json.template", "ent-core.json")
		}

		project.task("generateTestConf") << {
			FileUtils.createFile("test.properties", "ent-core.json.template", "ent-core.embedded.json")
		}

		project.task("extractDeployments") << {
			extractDeployments(project)
		}

		project.task("init") << {
			extractDeployments(project)
			initFiles(project)
		}

		project.task(dependsOn: ['runEnt', 'compileTestScala'], "integrationTest") << {
			gatling(project)
			stopEnt(project)
		}

		project.task(dependsOn: 'generateTestConf', "runEnt") << {
			runEnt(project)
		}

		project.task("stopEnt") << {
			stopEnt(project)
		}

	}

	private ExecResult runEnt(Project project) {
		project.exec {
			workingDir '.'

			if (System.env.OS != null && System.env.OS.contains('Windows')) {
				commandLine 'cmd', '/c', 'run.bat'
			} else {
				commandLine './run.sh'
			}
		}
	}

	private ExecResult stopEnt(Project project) {
		project.exec {
			workingDir '.'

			if (System.env.OS != null && System.env.OS.contains('Windows')) {
				commandLine 'cmd', '/c', 'stop.bat'
			} else {
				commandLine './stop.sh'
			}
		}
	}

	private void gatling(Project project) {
		def simulations = new File(project.sourceSets.test.output.classesDir.getPath() + File.separator + 'org' + File.separator + 'entcore' + File.separator + 'test' + File.separator + 'simulations')

		project.logger.lifecycle(" ---- Executing all Gatling scenarios from: ${simulations} ----")
		simulations.eachFileRecurse { file ->
			if (file.isFile()) {
				//Remove the full path, .class and replace / with a .
				project.logger.debug("Tranformed file ${file} into")
				def gatlingScenarioClass = (file.getPath() - (project.sourceSets.test.output.classesDir.getPath() + File.separator) - '.class')
						.replace(File.separator, '.')

				project.logger.debug("Tranformed file ${file} into scenario class ${gatlingScenarioClass}")
				System.setProperty("gatling.http.connectionTimeout", "300000")
				project.javaexec {
					main = 'io.gatling.app.Gatling'
					classpath = project.sourceSets.test.output + project.sourceSets.test.runtimeClasspath
					args '-rbf',
							project.sourceSets.test.output.classesDir,
							'-s',
							gatlingScenarioClass,
							'-rf',
							'build/reports/gatling'
				}
			}
		}
		project.logger.lifecycle(" ---- Done executing all Gatling scenarios ----")
	}

	private void extractDeployments(Project project) {
		if (!project.file("deployments")?.exists()) {
			project.file("deployments").mkdir()
		}
		project.copy {
			from {
				project.configurations.deployment.collect { project.zipTree(it) }
			}
			into "deployments/"
		}
		project.file("deployments/org")?.deleteDir()
		project.file("deployments/com")?.deleteDir()
		project.file("deployments/fr")?.deleteDir()
		project.file("deployments/errors")?.deleteDir()
		project.file("deployments/META-INF")?.deleteDir()
		project.file("deployments/org")?.deleteDir()
		project.file("deployments/git-hash")?.delete()
	}

	def initFiles(Project project) {
		File runsh = project.file("run.sh")
		File runbat = project.file("run.bat")
		File stopsh = project.file("stop.sh")
		File stopbat = project.file("stop.bat")
		stopbat.write("wmic process where \"name like '%%java%%'\" delete")
		stopsh.write(
				"#!/bin/sh\n" +
						"PID_ENT=\$(ps -ef | grep vertx | grep -v grep | sed 's/\\s\\+/ /g' | cut -d' ' -f2)\n" +
						"kill \$PID_ENT"
		)
		String version = project.getProperties().get("entCoreVersion")
		runbat.write(
				"SET PATH=%PATH%;tools\\vertx\\bin;tools\\gradle\\bin;tools\\jdk\\bin\n" +
						"\n" +
						"tasklist /nh /fi \"imagename eq mongod.exe\" | find /i \"mongod.exe\" > nul || (start cmd /k tools\\mongodb\\bin\\mongod.exe --dbpath tools\\mongodb-data)\n" +
						"vertx runMod org.entcore~infra~" + version + " -conf ent-core.embedded.json"
		)
		runsh.write(
				"#!/bin/bash\n" +
						"vertx runMod org.entcore~infra~" + version + " -conf ent-core.embedded.json &"
		)

		runsh.setExecutable(true, true)
		stopsh.setExecutable(true, true)

		project.file("scripts")?.mkdir()
		project.file("sample-be1d/Ecole primaire Emile Zola")?.mkdirs()
		project.file("data/tests")?.mkdirs()
		project.file("src/test/scala/org/entcore/test/scenarios")?.mkdirs()
		project.file("src/test/scala/org/entcore/test/simulations")?.mkdir()

		File schema = project.file("scripts/schema.cypher")
		InputStream is = this.getClass().getClassLoader().getResourceAsStream("scripts/schema.cypher")
		FileUtils.copy(is, schema)

		File scn = project.file("src/test/scala/org/entcore/test/scenarios/IntegrationTestScenario.scala")
		InputStream scnis = this.getClass().getClassLoader()
				.getResourceAsStream("src/test/scala/org/entcore/test/scenarios/IntegrationTestScenario.scala")
		FileUtils.copy(scnis, scn)

		File sim = project.file("src/test/scala/org/entcore/test/simulations/IntegrationTest.scala")
		InputStream simis = this.getClass().getClassLoader()
				.getResourceAsStream("src/test/scala/org/entcore/test/simulations/IntegrationTest.scala")
		FileUtils.copy(simis, sim)

		File i0 = project.file("sample-be1d/Ecole primaire Emile Zola/CSVExtraction-eleves.csv")
		File i1 = project.file("sample-be1d/Ecole primaire Emile Zola/CSVExtraction-enseignants.csv")
		File i2 = project.file("sample-be1d/Ecole primaire Emile Zola/CSVExtraction-responsables.csv")
		InputStream is0 = this.getClass().getClassLoader()
				.getResourceAsStream("sample-be1d/Ecole primaire Emile Zola/CSVExtraction-eleves.csv")
		InputStream is1 = this.getClass().getClassLoader()
				.getResourceAsStream("sample-be1d/Ecole primaire Emile Zola/CSVExtraction-enseignants.csv")
		InputStream is2 = this.getClass().getClassLoader()
				.getResourceAsStream("sample-be1d/Ecole primaire Emile Zola/CSVExtraction-responsables.csv")
		FileUtils.copy(is0, i0)
		FileUtils.copy(is1, i1)
		FileUtils.copy(is2, i2)

		File entcoreJsonTemplate = project.file("ent-core.json.template")
		FileUtils.copy(this.getClass().getClassLoader().getResourceAsStream("ent-core.json.template"),
				entcoreJsonTemplate)

		String filename = "conf.properties"
		File confProperties = project.file(filename)
		Map confMap = FileUtils.createOrAppendProperties(confProperties, filename)

		String filenameTest = "test.properties"
		File testProperties = project.file(filenameTest)
		Map testMap = FileUtils.createOrAppendProperties(testProperties, filenameTest)


		Map appliPort = [:]
		project.file("deployments").eachDir {
			it.eachDir { dir ->
				String dest = "migration".equals(dir.name) ? dir.name + File.separator + it.name : dir.name
				new AntBuilder().copy( todir: dest ) {
					fileset( dir: dir.absolutePath )
				}
			}
			it.eachFile(FileType.FILES) { file ->
				File f
				switch (file.name) {
					case "conf.json.template":
						f = entcoreJsonTemplate
						f.append(",\n")
						f.append(file.text)
						file.eachLine { line ->
							def matcher = line =~ /\s*\t*\s*"port"\s*:\s*([0-9]+)[,]?\s*\t*\s*/
							if (matcher.find()) {
								appliPort.put(it.name, matcher.group(1))
							}
						}
						break;
					case "conf.properties" :
						FileUtils.appendProperties(project, file, confMap)
						break;
					case "test.properties" :
						FileUtils.appendProperties(project, file, testMap)
						break;
				}
			}
		}

		InputStream httpProxy = this.getClass().getClassLoader().getResourceAsStream("http-proxy.json.template")
		entcoreJsonTemplate.append(",\n")
		entcoreJsonTemplate.append(httpProxy.text)
		appliPort.each { k, v ->
			entcoreJsonTemplate.append(
					",\n" +
					"          {\n" +
					"            \"location\": \"/" + k + "\",\n" +
					"            \"proxy_pass\": \"http://localhost:" + v + "\"\n" +
					"          }"
			)
		}
		entcoreJsonTemplate.append(
				"        ]\n" +
				"      }\n" +
				"    }\n" +
				"<% } %>" +
				"  ]\n" +
				"}"
		)

		if (!confMap.containsKey("entcoreVersion")) {
			confProperties.append("\nentcoreVersion=" + version)
		}
		if (!testMap.containsKey("entcoreVersion")) {
			testProperties.append("\nentcoreVersion=" + version)
		}
	}

}