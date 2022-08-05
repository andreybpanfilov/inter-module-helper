# About

The purpose of **inter-module helper maven extension** is to overcome following well-known maven issue:

## [Root-reactor aware subfolder builds](https://maarten.mulders.it/2020/11/whats-new-in-maven-4/#root-reactor-aware-subfolder-builds)


> Let’s assume we have a Maven project with three modules: common, client and app. The modules depend on each other:
>
> ```
>       [app]
>      /     \
>     V       V
>[client] -> [common] 
>```
>
>Imagine the app module is a web application. With Maven 3, when you wanted to start it to see if it works, you might use for example `mvn jetty:run`. But if you run that from the root project, it will run `jetty:run` in client and common as well. Since those modules do not contain a web application, it will fail.
>
>Running `mvn jetty:run` inside the app module didn't work either, because Maven would not be able to resolve the client and common modules. That's why many people got into the bad habit of running mvn install first, as a workaround, so Maven would resolve client and common from the local repository. The same was true for selecting the project to build with `mvn -f app/pom.xml` - again, Maven would not be able to resolve the client and common modules.


# Usage in Maven build
To take benefit of **fakerepo maven extension** please create in root of your multi module project `.mvn/extensions.xml` file and place there:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>tel.panfilov.maven</groupId>
        <artifactId>inter-module-extension</artifactId>
        <version>0.1.6</version>
    </extension>
</extensions>
```

after that if you specify `-Dimh.ext` system property, `maven` will be able to discover previously packaged artifacts of multi-module project. For example, for the usecase described above the sequence of commands would be following:
```shell
mvn clean package
mvn jetty:run -pl app -Dimh.ext
```