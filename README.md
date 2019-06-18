# Prosessering av legeerklæringer (PALE)
Repository for Pale. Application written in Kotlin used to receive legeerklæringer from external systems, doing some validation, then pushing it to our internal systems.

## Technologies used
* Kotlin
* Gradle

#### Requirements

* JDK 11


#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`


#### Creating a docker image
Creating a docker image should be as simple as `docker build -t pale-2 .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 pale-2`

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`

### For NAV employees
We are available at the Slack channel #barken
