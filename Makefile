APP_NAME := s3browser
IMAGE ?= $(APP_NAME):latest
PORT ?= 8080
DEBUG_PORT ?= 5005
MAVEN_IMAGE ?= maven:3.9.9-eclipse-temurin-21
WORKDIR := /workspace

.PHONY: help build run debug mvn-run mvn-debug docker-build docker-run docker-debug docker-stop docker-logs test

help:
	@echo "Available targets:"
	@echo "  make build         - Build jar in Dockerized Maven (no local Java needed)"
	@echo "  make run           - Run app with Spring Boot in Dockerized Maven on $(PORT)"
	@echo "  make debug         - Run app in debug mode (JDWP on $(DEBUG_PORT))"
	@echo "  make mvn-run       - Run app locally with mvn spring-boot:run"
	@echo "  make mvn-debug     - Run app locally with mvn spring-boot:run in debug mode"
	@echo "  make test          - Run tests in Dockerized Maven"
	@echo "  make docker-build  - Build runtime Docker image $(IMAGE)"
	@echo "  make docker-run    - Run runtime Docker image on $(PORT)"
	@echo "  make docker-debug  - Run runtime Docker image with JDWP on $(DEBUG_PORT)"
	@echo "  make docker-stop   - Stop running container named $(APP_NAME)"
	@echo "  make docker-logs   - Tail logs of container $(APP_NAME)"

build:
	docker run --rm \
		-v "$(CURDIR):$(WORKDIR)" \
		-w "$(WORKDIR)" \
		$(MAVEN_IMAGE) \
		mvn -DskipTests package

run:
	docker run --rm -it \
		-p $(PORT):8080 \
		-v "$(CURDIR):$(WORKDIR)" \
		-w "$(WORKDIR)" \
		$(MAVEN_IMAGE) \
		mvn spring-boot:run

debug:
	docker run --rm -it \
		-p $(PORT):8080 \
		-p $(DEBUG_PORT):$(DEBUG_PORT) \
		-v "$(CURDIR):$(WORKDIR)" \
		-w "$(WORKDIR)" \
		$(MAVEN_IMAGE) \
		mvn spring-boot:run -Dspring-boot.run.jvmArguments='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}'

# Local Maven run (requires Java/Maven installed on host).
mvn-run:
	mvn spring-boot:run

# Local Maven debug run (requires Java/Maven installed on host).
mvn-debug:
	mvn spring-boot:run -Dspring-boot.run.jvmArguments='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}'

test:
	docker run --rm \
		-v "$(CURDIR):$(WORKDIR)" \
		-w "$(WORKDIR)" \
		$(MAVEN_IMAGE) \
		mvn test

docker-build:
	docker build -t $(IMAGE) .

docker-run:
	docker run --rm -it \
		--name $(APP_NAME) \
		-p $(PORT):8080 \
		$(IMAGE)

# Runs the packaged image and enables remote debugging via JAVA_TOOL_OPTIONS.
docker-debug:
	docker run --rm -it \
		--name $(APP_NAME) \
		-p $(PORT):8080 \
		-p $(DEBUG_PORT):$(DEBUG_PORT) \
		-e JAVA_TOOL_OPTIONS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}' \
		$(IMAGE)

docker-stop:
	-docker stop $(APP_NAME)

docker-logs:
	docker logs -f $(APP_NAME)
