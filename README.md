# wsrpc-maven-plugin
WSRPC Maven Plugin to Generate Code

This maven plugin will generated the needed source files as determined by a WebSocket RPC (wsrpc) specification file.
The specification file is a JSON file that defines the wsrpc interface that is to be generated. 

## Usage
To use this plugin, add it to your maven project descriptor pom.xml.
The following example shows how to generate a interfaces defined in the "wsrpc" subdirectory.

```
<plugin>
	<groupId>net.psgglobal.plugin</groupId>
	<artifactId>wsrpc-maven-plugin</artifactId>
	<version>1.0-SNAPSHOT</version>
	<configuration>
		<inputDir>${project.sourceDirectory}/main/resources/wsrpc</inputDir>
	</configuration>
	<executions>
		<execution>
			<phase>generate-sources</phase>
			<goals>
				<goal>generate</goal>
			</goals>
		</execution>
	</executions>
</plugin>
```
All files ending in `.json` will be processed.

## Specification files
A specification file is a standard JSON file that describes the interface.
A specification file contains a number of sections which, all but the header section, are optional.
The sections are:

1. The header definition
2. Constants definition
3. Class definitions
4. List definitions
5. Request definitions
6. Notice definitions

### Specification header definition
The header section contains the following properties:

| property | Description | Usage |
| -------- | ----------- | ----- |
| name | This is the name of the interface | Mandatory |
| package | This is the Java package name for the generated Java classes | Mandatory |
| supportBinaryData | If true, then binary websocket IO will be used | Defaults to false |

 