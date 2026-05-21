///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//JAVA 17+

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "MavenHexagonalScaffold", mixinStandardHelpOptions = true, version = "1.0",
        description = "Genera un proyecto base Spring Boot Reactivo multimódulo.")
public class MavenHexagonalScaffold implements Runnable {

    @Option(names = {"-n", "--service-name"},
            description = "Nombre del microservicio",
            defaultValue = "mi-microservicio",
            required = true)
    private String projectName;

    @Option(names = {"-d", "--database"},
            description = "Base de datos a configurar: postgres, mongo",
            defaultValue = "postgres",
            required = true)
    private String database;

    @Option(names = {"-m", "--messaging-system"},
            description = "Sistema de mensajeria a configurar: rabbit-producer, rabbit-consumer",
            defaultValue = "none")
    private String messagingSystem;

    public static void main(String... args) {
        int exitCode = new CommandLine(new MavenHexagonalScaffold()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            String safeProjectName = projectName.replace("-", "");
            Path rootPath = Paths.get(projectName);
            System.out.println("[INFO] Creando proyecto: " + projectName);

            String dbAdapterModule = "mongo".equalsIgnoreCase(database)
                    ? "infrastructure/driven-adapters/mongo"
                    : "infrastructure/driven-adapters/postgres";

            List<String> modules = new java.util.ArrayList<>(List.of(
                    "domain/model",
                    "application/use-cases",
                    dbAdapterModule,
                    "infrastructure/entry-points/rest-api",
                    "infrastructure/entry-points/app"
            ));

            // Agregar modulo de mensajeria si esta configurado
            String messagingModule = null;
            if ("rabbit-producer".equalsIgnoreCase(messagingSystem)) {
                messagingModule = "infrastructure/driven-adapters/rabbit-producer";
                modules.add(messagingModule);
            } else if ("rabbit-consumer".equalsIgnoreCase(messagingSystem)) {
                messagingModule = "infrastructure/entry-points/rabbit-consumer";
                modules.add(messagingModule);
            }

            for (String module : modules) {
                String moduleName = module.substring(module.lastIndexOf("/") + 1).replace("-", "");
                String basePackage = "com." + safeProjectName + "." + moduleName;
                String packagePath = "/src/main/java/" + basePackage.replace(".", "/");
                Files.createDirectories(rootPath.resolve(module + packagePath));

                String modulePom = getModulePomTemplate(projectName, safeProjectName, module);
                Files.writeString(rootPath.resolve(module + "/pom.xml"), modulePom);

                // Si es el módulo rest-api, creamos el controlador
                if (module.equals("infrastructure/entry-points/rest-api")) {
                    String helloController = String.format("""
package %s;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HelloController {
    @GetMapping("/hello")
    public Mono<String> sayHello() {
        return Mono.just("¡Hola desde el scaffold Hexagonal Reactivo!");
    }
}
""", basePackage);
                    Files.writeString(rootPath.resolve(module + packagePath + "/HelloController.java"), helloController);
                }

                // Si es el módulo de entry-points/app principal, creamos la app
                if (module.equals("infrastructure/entry-points/app")) {
                    String mainPackage = "com." + safeProjectName;
                    String mainClassPath = "/src/main/java/" + mainPackage.replace(".", "/");
                    Files.createDirectories(rootPath.resolve(module + mainClassPath));

                    // También necesitamos una clase Main para que Spring Boot arranque
                    String mainClass = String.format("""
package %s;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }
}
""", mainPackage);
                    Files.writeString(rootPath.resolve(module + mainClassPath + "/MainApplication.java"), mainClass);

                    // Crear clase de configuración para inyectar casos de uso sin anotaciones de Spring
                    String applicationConfig = String.format("""
package %s;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(
        basePackages = {
                "%s.usecases",
                "%s.restapi",
                "%s.app"
        },
        includeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = ".*UseCase?$"
                )
        },
        useDefaultFilters = false
)
public class ApplicationConfig {
}
""", mainPackage, mainPackage, mainPackage, mainPackage);
                    Files.writeString(rootPath.resolve(module + mainClassPath + "/ApplicationConfig.java"), applicationConfig);

                    // 1. Crear el directorio de recursos
                    Path resourcesPath = rootPath.resolve(module + "/src/main/resources");
                    Files.createDirectories(resourcesPath);

                    // 3. Escribir el archivo YAML
                    Files.writeString(resourcesPath.resolve("application.yml"), getYamlContent());
                }
            }

            // Crear archivos de mensajeria si esta configurado
            if ("rabbit-producer".equalsIgnoreCase(messagingSystem)) {
                createRabbitProducerFiles(rootPath, safeProjectName);
            } else if ("rabbit-consumer".equalsIgnoreCase(messagingSystem)) {
                createRabbitConsumerFiles(rootPath, safeProjectName);
            }

            // 3. Crear archivos .env y .env.example
            Files.writeString(rootPath.resolve(".env"), getEnvContent());
            Files.writeString(rootPath.resolve(".env.example"), getEnvExampleContent());

            // 4. Crear .gitignore
            String gitIgnore = """
target/
!.mvn/wrapper/maven-wrapper.jar
*.class
*.log
*.ctxt
.mtj.tmp/
*.jar
*.war
*.ear
*.zip
*.tar.gz
*.rar
hs_err_pid*
.idea/
*.iml
.classpath
.project
.settings/
bin/
.vscode/
.env
""";
            Files.writeString(rootPath.resolve(".gitignore"), gitIgnore);

            String pomContent = getRootPomTemplate(projectName, database, messagingSystem);
            Files.writeString(rootPath.resolve("pom.xml"), pomContent);

            System.out.println("[SUCCESS] Proyecto creado en: " + rootPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo crear el proyecto: " + e.getMessage());
        }
    }

    private String getYamlContent() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("spring:\n");
        if ("mongo".equalsIgnoreCase(database)) {
            yaml.append("  data:\n");
            yaml.append("    mongodb:\n");
            yaml.append("      uri: ${MONGODB_URI}\n");
        } else {
            yaml.append("  r2dbc:\n");
            yaml.append("    url: ${R2DBC_URL}\n");
            yaml.append("    username: ${DB_USERNAME}\n");
            yaml.append("    password: ${DB_PASSWORD}\n");
        }
        if ("rabbit-producer".equalsIgnoreCase(messagingSystem) || "rabbit-consumer".equalsIgnoreCase(messagingSystem)) {
            yaml.append("  rabbitmq:\n");
            yaml.append("    host: ${RABBITMQ_HOST}\n");
            yaml.append("    port: ${RABBITMQ_PORT}\n");
            yaml.append("    username: ${RABBITMQ_USERNAME}\n");
            yaml.append("    password: ${RABBITMQ_PASSWORD}\n");
        }
        yaml.append("server:\n");
        yaml.append("  port: ${SERVER_PORT}\n");
        return yaml.toString();
    }

    private String getEnvContent() {
        StringBuilder env = new StringBuilder();
        env.append("# ===================================================================\n");
        env.append("# Environment Variables - ").append(projectName).append("\n");
        env.append("# ===================================================================\n");
        env.append("# IMPORTANT: This file contains sensitive credentials.\n");
        env.append("# DO NOT commit this file to version control.\n");
        env.append("# Copy .env.example to .env and fill in the actual values.\n");
        env.append("# ===================================================================\n\n");

        env.append("# Server\n");
        env.append("SERVER_PORT=8080\n\n");

        env.append("# Database\n");
        if ("mongo".equalsIgnoreCase(database)) {
            env.append("MONGODB_URI=mongodb://localhost:27017/mydb\n");
        } else {
            env.append("R2DBC_URL=r2dbc:postgresql://localhost:5432/mydb\n");
            env.append("DB_USERNAME=postgres\n");
            env.append("DB_PASSWORD=password\n");
        }

        if ("rabbit-producer".equalsIgnoreCase(messagingSystem) || "rabbit-consumer".equalsIgnoreCase(messagingSystem)) {
            env.append("\n# RabbitMQ\n");
            env.append("RABBITMQ_HOST=localhost\n");
            env.append("RABBITMQ_PORT=5672\n");
            env.append("RABBITMQ_USERNAME=guest\n");
            env.append("RABBITMQ_PASSWORD=guest\n");
        }

        return env.toString();
    }

    private String getEnvExampleContent() {
        StringBuilder env = new StringBuilder();
        env.append("# ===================================================================\n");
        env.append("# Environment Variables Template - ").append(projectName).append("\n");
        env.append("# ===================================================================\n");
        env.append("# Copy this file to .env and fill in the actual values.\n");
        env.append("# ===================================================================\n\n");

        env.append("# Server\n");
        env.append("SERVER_PORT=8080\n\n");

        env.append("# Database\n");
        if ("mongo".equalsIgnoreCase(database)) {
            env.append("MONGODB_URI=mongodb://localhost:27017/mydb\n");
        } else {
            env.append("R2DBC_URL=r2dbc:postgresql://localhost:5432/mydb\n");
            env.append("DB_USERNAME=\n");
            env.append("DB_PASSWORD=\n");
        }

        if ("rabbit-producer".equalsIgnoreCase(messagingSystem) || "rabbit-consumer".equalsIgnoreCase(messagingSystem)) {
            env.append("\n# RabbitMQ\n");
            env.append("RABBITMQ_HOST=localhost\n");
            env.append("RABBITMQ_PORT=5672\n");
            env.append("RABBITMQ_USERNAME=\n");
            env.append("RABBITMQ_PASSWORD=\n");
        }

        return env.toString();
    }

    private String getRootPomTemplate(String name, String dbType, String messaging) {
        String dbModuleName = "mongo".equalsIgnoreCase(dbType) ? "mongo" : "postgres";
        StringBuilder modulesSection = new StringBuilder();
        modulesSection.append("                <module>domain/model</module>\n");
        modulesSection.append("                <module>application/use-cases</module>\n");
        modulesSection.append("                <module>infrastructure/driven-adapters/").append(dbModuleName).append("</module>\n");
        modulesSection.append("                <module>infrastructure/entry-points/rest-api</module>\n");
        modulesSection.append("                <module>infrastructure/entry-points/app</module>\n");
        if ("rabbit-producer".equalsIgnoreCase(messaging)) {
            modulesSection.append("                <module>infrastructure/driven-adapters/rabbit-producer</module>\n");
        } else if ("rabbit-consumer".equalsIgnoreCase(messaging)) {
            modulesSection.append("                <module>infrastructure/entry-points/rabbit-consumer</module>\n");
        }
        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.%s</groupId>
    <artifactId>%s</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>
    <properties>
        <java.version>21</java.version>
    </properties>
    <modules>
%s    </modules>
    <dependencies>
        <dependency>
            <groupId>me.paulschwarz</groupId>
            <artifactId>spring-dotenv</artifactId>
            <version>4.0.0</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
""".formatted(name.replace("-", ""), name, modulesSection.toString());
    }

    private String getModulePomTemplate(String parentArtifactId, String safeProjectName, String modulePath) {
        String moduleArtifactId = modulePath.replace("/", "-");
        String modulePackageName = modulePath.substring(modulePath.lastIndexOf("/") + 1).replace("-", "");
        boolean isDbAdapter = modulePath.startsWith("infrastructure/driven-adapters/");
        boolean isEntryPoints = modulePath.startsWith("infrastructure/entry-points/");
        boolean isInfrastructure = isDbAdapter || isEntryPoints;
        String relativePath = isInfrastructure ? "../../../pom.xml" : "../../pom.xml";
        StringBuilder sb = new StringBuilder();

        sb.append("""
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.%s</groupId>
        <artifactId>%s</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>%s</relativePath>
    </parent>
    <groupId>com.%s.%s</groupId>
    <artifactId>%s</artifactId>
    <dependencies>
""".formatted(safeProjectName, parentArtifactId, relativePath, safeProjectName, modulePackageName, moduleArtifactId));

        // Inyección de dependencias específicas por módulo
        if (isDbAdapter) {
            if (modulePath.endsWith("/mongo")) {
                sb.append("""
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
</dependency>
""");
            } else if (modulePath.endsWith("/rabbit-producer")) {
                sb.append("""
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
""");
            } else {
                sb.append("""
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
""");
            }
        } else if (modulePath.equals("infrastructure/entry-points/rest-api")) {
            sb.append("""
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
""");
        } else if (modulePath.equals("infrastructure/entry-points/app")) {
            sb.append(String.format("""
<dependency>
    <groupId>com.%s.restapi</groupId>
    <artifactId>infrastructure-entry-points-rest-api</artifactId>
    <version>${project.version}</version>
</dependency>
""", safeProjectName));
        } else if (modulePath.equals("infrastructure/entry-points/rabbit-consumer")) {
            sb.append("""
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
""");
        }

        sb.append("</dependencies>\n");

        // Solo el módulo app necesita el spring-boot-maven-plugin para repackage
        if (modulePath.equals("infrastructure/entry-points/app")) {
            sb.append("""
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
""");
        }

        sb.append("</project>\n");
        return sb.toString();
    }

    private void createRabbitProducerFiles(Path rootPath, String safeProjectName) throws IOException {
        String modulePath = "infrastructure/driven-adapters/rabbit-producer";
        String moduleName = "rabbitproducer";
        String basePackage = "com." + safeProjectName + "." + moduleName;
        String packagePath = "/src/main/java/" + basePackage.replace(".", "/");

        // RabbitMQ configuration
        String configClass = "package " + basePackage + ";\n\n" +
            "import org.springframework.amqp.core.Queue;\n" +
            "import org.springframework.amqp.rabbit.connection.ConnectionFactory;\n" +
            "import org.springframework.amqp.rabbit.core.RabbitTemplate;\n" +
            "import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;\n" +
            "import org.springframework.amqp.support.converter.MessageConverter;\n" +
            "import org.springframework.context.annotation.Bean;\n" +
            "import org.springframework.context.annotation.Configuration;\n\n" +
            "@Configuration\n" +
            "public class RabbitMQConfig {\n" +
            "    public static final String QUEUE_NAME = \"messages\";\n\n" +
            "    @Bean\n" +
            "    public Queue messageQueue() {\n" +
            "        return new Queue(QUEUE_NAME, true);\n" +
            "    }\n\n" +
            "    @Bean\n" +
            "    public MessageConverter jsonMessageConverter() {\n" +
            "        return new Jackson2JsonMessageConverter();\n" +
            "    }\n\n" +
            "    @Bean\n" +
            "    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {\n" +
            "        RabbitTemplate template = new RabbitTemplate(connectionFactory);\n" +
            "        template.setMessageConverter(jsonMessageConverter());\n" +
            "        return template;\n" +
            "    }\n" +
            "}\n";
        Files.writeString(rootPath.resolve(modulePath + packagePath + "/RabbitMQConfig.java"), configClass);

        // Message publisher
        String publisher = "package " + basePackage + ";\n\n" +
            "import reactor.core.publisher.Mono;\n" +
            "import org.springframework.amqp.rabbit.core.RabbitTemplate;\n" +
            "import org.springframework.stereotype.Component;\n\n" +
            "@Component\n" +
            "public class MessagePublisher {\n\n" +
            "    private final RabbitTemplate rabbitTemplate;\n\n" +
            "    public MessagePublisher(RabbitTemplate rabbitTemplate) {\n" +
            "        this.rabbitTemplate = rabbitTemplate;\n" +
            "    }\n\n" +
            "    public Mono<Void> publish(Object message) {\n" +
            "        return Mono.fromRunnable(() ->\n" +
            "            rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, message)\n" +
            "        );\n" +
            "    }\n" +
            "}\n";
        Files.writeString(rootPath.resolve(modulePath + packagePath + "/MessagePublisher.java"), publisher);
    }

    private void createRabbitConsumerFiles(Path rootPath, String safeProjectName) throws IOException {
        String modulePath = "infrastructure/entry-points/rabbit-consumer";
        String moduleName = "rabbitconsumer";
        String basePackage = "com." + safeProjectName + "." + moduleName;
        String packagePath = "/src/main/java/" + basePackage.replace(".", "/");

        // RabbitMQ consumer configuration
        String configClass = "package " + basePackage + ";\n\n" +
            "import org.springframework.amqp.core.Queue;\n" +
            "import org.springframework.amqp.rabbit.connection.ConnectionFactory;\n" +
            "import org.springframework.amqp.rabbit.core.RabbitTemplate;\n" +
            "import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;\n" +
            "import org.springframework.amqp.support.converter.MessageConverter;\n" +
            "import org.springframework.context.annotation.Bean;\n" +
            "import org.springframework.context.annotation.Configuration;\n\n" +
            "@Configuration\n" +
            "public class RabbitMQConfig {\n" +
            "    public static final String QUEUE_NAME = \"messages\";\n\n" +
            "    @Bean\n" +
            "    public Queue messageQueue() {\n" +
            "        return new Queue(QUEUE_NAME, true);\n" +
            "    }\n\n" +
            "    @Bean\n" +
            "    public MessageConverter jsonMessageConverter() {\n" +
            "        return new Jackson2JsonMessageConverter();\n" +
            "    }\n\n" +
            "    @Bean\n" +
            "    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {\n" +
            "        RabbitTemplate template = new RabbitTemplate(connectionFactory);\n" +
            "        template.setMessageConverter(jsonMessageConverter());\n" +
            "        return template;\n" +
            "    }\n" +
            "}\n";
        Files.writeString(rootPath.resolve(modulePath + packagePath + "/RabbitMQConfig.java"), configClass);

        // Message listener adapter (receptor)
        String listener = "package " + basePackage + ";\n\n" +
            "import org.slf4j.Logger;\n" +
            "import org.slf4j.LoggerFactory;\n" +
            "import org.springframework.amqp.rabbit.annotation.RabbitListener;\n" +
            "import org.springframework.stereotype.Component;\n\n" +
            "@Component\n" +
            "public class MessageListener {\n\n" +
            "    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);\n\n" +
            "    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)\n" +
            "    public void handleMessage(Object message) {\n" +
            "        log.info(\"Mensaje recibido: {}\", message);\n" +
            "    }\n" +
            "}\n";
        Files.writeString(rootPath.resolve(modulePath + packagePath + "/MessageListener.java"), listener);
    }
}
