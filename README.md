marathon-maven-plugin
=====================

Maven plugin for interacting with Marathon

It can both process `marathon.json` config and use it to push it to Marathon host for deployment. 

# Maven documentation

For detailed documentation of goals and configuration options run in your project:
`mvn com.holidaycheck:marathon-maven-plugin:help -Ddetail=true`

# Basic usage

This plugin plays well with [docker-maven-plugin](https://github.com/spotify/docker-maven-plugin)
so you might want to use it e.g. to include proper versioning of Docker `image` in `marathon.json`.

Add plugin configuration to your `pom.xml`:

```xml
<plugin>
	<groupId>com.holidaycheck</groupId>
	<artifactId>marathon-maven-plugin</artifactId>
	<version>0.0.2</version>
	<configuration>
		<image>${docker-image-prefix}/${project.build.finalName}:${project.version}-${gitShortCommitId}</image>
		<marathonHost>http://${mesos.host}:${mesos.port}</marathonHost>
	</configuration>
	<executions>
		<execution>
			<id>processConfig</id>
			<phase>install</phase>
			<goals>
				<goal>processConfig</goal>
			</goals>
		</execution>
		<execution>
			<id>deploy</id>
			<phase>deploy</phase>
			<goals>
				<goal>deploy</goal>
			</goals>
		</execution>
		<execution>
			<id>apptasks</id>
			<phase>pre-integration-test</phase>
			<goals>
				<goal>apptasks</goal>
			</goals>
			<configuration>
				<propertyPrefix>mesos-</propertyPrefix>
				<delay>5</delay>
			</configuration>
		</execution>
		<execution>
			<id>delete</id>
			<phase>post-integration-test</phase>
			<goals>
				<goal>delete</goal>
			</goals>
		</execution>
	</executions>
</plugin>
```

By default your template `marathon.json` should be in the root project directory.

# Deploy an applications group

To deploy an applications group, add a `group` element with the content `true` to your plugin configurarition :

Notice that the process-config goal is skipped, as we do the filtering of the marathon json file in a previous phase
with the maven-resources-plugin.

```xml
<plugin>
    <groupId>com.holidaycheck</groupId>
    <artifactId>marathon-maven-plugin</artifactId>
    <version>0.0.2-SNAPSHOT</version>
    <configuration>
        <marathonHost>http://${mesos.host}:${mesos.port}</marathonHost>
        <group>true</group>
    </configuration>
    <executions>
        <execution>
            <id>deploy</id>
            <phase>deploy</phase>
            <goals>
                <goal>deploy</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

# Remove an older group or application from Marathon before deploying

Use the `deleteBeforeDeploy` configuration element with a value of `true` do delete
the application or group on the Marathon/Mesos cluster, if it exists, before deploying.

```xml
<plugin>
    <groupId>com.holidaycheck</groupId>
    <artifactId>marathon-maven-plugin</artifactId>
    <version>0.0.2-SNAPSHOT</version>
    <configuration>
        <marathonHost>http://${mesos.host}:${mesos.port}</marathonHost>
        <group>true</group>
        <deleteBeforeDeploy>true</deleteBeforeDeploy>
    </configuration>
    <executions>
        <execution>
            <id>deploy</id>
            <phase>deploy</phase>
            <goals>
                <goal>deploy</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
