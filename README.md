# About

The purpose of **inter-module helper maven extension** is to overcome following well-known maven issue:

## [Root-reactor aware subfolder builds](https://maarten.mulders.it/2020/11/whats-new-in-maven-4/#root-reactor-aware-subfolder-builds)


> Let’s assume we have a Maven project with three modules: common, client and app. The modules depend on each other:
>
> ```
>        [app]
>      /       \
>     V         V
> [client] -> [common] 
>```
>
>Imagine the app module is a web application. With Maven 3, when you wanted to start it to see if it works, you might use for example `mvn jetty:run`. But if you run that from the root project, it will run `jetty:run` in client and common as well. Since those modules do not contain a web application, it will fail.
>
>Running `mvn jetty:run` inside the app module didn't work either, because Maven would not be able to resolve the client and common modules. That's why many people got into the [bad habit](https://www.youtube.com/watch?v=2HyGxtsDf60) of running mvn install first, as a workaround, so Maven would resolve client and common from the local repository. The same was true for selecting the project to build with `mvn -f app/pom.xml` - again, Maven would not be able to resolve the client and common modules.


# Usage in Maven build

To take benefit of **inter-module helper maven extension** create in root of your multi-module project `.mvn/extensions.xml` file and place there:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>tel.panfilov.maven</groupId>
        <artifactId>inter-module-extension</artifactId>
        <version>0.1.7</version>
    </extension>
</extensions>
```

### First option

Specifying `-Dimh.workspace` system property during build, will cause `maven` to discover previously packaged artifacts of multi-module project, like it was implemented in `maven-4` (unfortunately, new behaviour introduced in `maven-4` does not cover cases with classifiers: [MNG-7527](https://issues.apache.org/jira/projects/MNG/issues/MNG-7527)). For example, for the usecase described above the sequence of commands would be following:
```shell
mvn clean package
mvn jetty:run -pl :app -Dimh.workspace
```

### Second option

Specifying `-Dimh.repository` system property, will cause `maven` to install artifacts into `target/local-repo` folder of root project instead of installing them into local repository folder (typically `~/.m2/repository`)

```shell
mvn clean install -Dimh.repository
mvn jetty:run -pl :app -Dimh.repository
```