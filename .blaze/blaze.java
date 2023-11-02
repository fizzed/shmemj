import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Task;
import com.fizzed.blaze.core.Blaze;
import com.fizzed.blaze.util.Globber;
import com.fizzed.buildx.Buildx;
import com.fizzed.buildx.ContainerBuilder;
import com.fizzed.buildx.Target;
import com.fizzed.jne.NativeTarget;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.Systems.cp;
import static com.fizzed.blaze.Systems.exec;
import static com.fizzed.blaze.util.Globber.globber;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class blaze {

    final private Logger log = Contexts.logger();

    private final Path projectDir = withBaseDir("..").toAbsolutePath();
    private final Path rustProjectDir = withBaseDir("../native").toAbsolutePath();
    private final NativeTarget localNativeTarget = NativeTarget.detect();

    @Task(order=10)
    public void build_natives() throws Exception {
        final String targetStr = Contexts.config().value("target").orNull();
        final NativeTarget nativeTarget = targetStr != null ? NativeTarget.fromJneTarget(targetStr) : NativeTarget.detect();
        final String jneTarget = nativeTarget.toJneTarget();
        final String rustTarget = nativeTarget.toRustTarget();

        final Path rustProjectDir = withBaseDir("../native");
        final Path rustArtifactDir = rustProjectDir.resolve("target/" + rustTarget + "/release");
        final Path javaOutputDir = withBaseDir("../shmemj-" + jneTarget + "/src/main/resources/jne/" + nativeTarget.toJneOsAbi() + "/" + nativeTarget.toJneArch());

        log.info("=================================================");
        log.info("  jneTarget: {}", nativeTarget.toJneTarget());
        log.info("  rustTarget: {}", nativeTarget.toRustTarget());
        log.info("  rustProjectDir: {}", rustProjectDir);
        log.info("  rustArtifactDir: {}", rustArtifactDir);
        log.info("  javaOutputDir: {}", javaOutputDir);
        log.info("=================================================");

        log.info("Building native lib...");

        exec("cargo", "build", "--release", "--target="+rustTarget)
            .workingDir(rustProjectDir)
            .verbose()
            .run();

        cp(globber(rustArtifactDir, "*.{so,dll,dylib}"))
            .target(javaOutputDir)
            .force()
            .verbose()
            .run();
    }

    @Task(order=20)
    public void clean_natives() throws Exception {
        exec("cargo", "clean")
            .workingDir(rustProjectDir)
            .verbose()
            .run();
    }

    @Task(order=30)
    public void test() throws Exception {
        exec("mvn", "clean", "test")
            .workingDir(projectDir)
            .verbose()
            .run();
    }

    private final List<Target> crossTargets = asList(

        //
        // Linux
        //

        new Target("linux", "x64", "x64 Ubuntu 16.04, JDK 11 cross compiler")
            .setTags("build", "test")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-x64"),

        new Target("linux", "arm64", "x64 Ubuntu 16.04, JDK 11 cross compiler")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-arm64"),

        // riscv64 only on ubuntu18+
        new Target("linux", "riscv64", "x64 Ubuntu 18.04, JDK 11 cross compiler")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-riscv64"),

        new Target("linux", "armhf", "x64 Ubuntu 16.04, JDK 11 cross compiler")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-armhf"),

        new Target("linux", "armel", "x64 Ubuntu 16.04, JDK 11 cross compiler")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-armel"),

        new Target("linux", "x32", "x64 Ubuntu 16.04, JDK 11 cross compiler")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-x32"),

        //
        // Linux (w/ MUSL)
        //

        new Target("linux_musl", "x64", "x64 Ubuntu 16.04, JDK 11 cross compiler")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux_musl-x64"),

        //
        // MacOS
        //

        new Target("macos", "x64", "MacOS 10.13")
            .setTags("build", "test")
            .setHost("bmh-build-x64-macos1013-1"),

        new Target("macos", "arm64", "MacOS 11")
            .setTags("build")
            .setHost("bmh-build-x64-macos11-1"),

        //
        // Windows
        //

        new Target("windows", "x64", "Windows 11")
            .setTags("build", "test")
            .setHost("bmh-build-x64-win11-1"),

        new Target("windows", "x32", "Windows 11")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),

        new Target("windows", "arm64", "Windows 11")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),

        //
        // FreeBSD
        //

        new Target("freebsd", "x64", "FreeBSD 12")
            .setTags("build", "test")
            .setHost("bmh-build-x64-freebsd12-1"),

        //
        // OpenBSD (will not compile due to pthread usage)
        //

        /*new Target("openbsd", "x64", "openbsd7.2")
            .setTags("build", "test")
            .setHost("bmh-build-x64-openbsd72-1"),*/

        //
        // CI/Test Local Machine
        //

        new Target(localNativeTarget.toJneOsAbi(), localNativeTarget.toJneArch(), "local machine")
            .setTags("test"),

        //
        // CI/Test Linux
        //

        new Target("linux", "x64", "Ubuntu 22.04, JDK 11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk11"),

        new Target("linux", "x64", "Ubuntu 22.04, JDK 17")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk17"),

        new Target("linux", "x64", "Ubuntu 22.04, JDK 21")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk21"),

        new Target("linux", "x32", "Ubuntu 16.04, JDK 11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x32-ubuntu16-jdk11"),

        new Target("linux", "arm64", "Ubuntu 16.04, JDK 11")
            .setTags("test")
            .setHost("bmh-hv-6")
            .setContainerImage("fizzed/buildx:arm64-ubuntu16-jdk11"),

        new Target("linux", "armhf", "Ubuntu 16.04, JDK 11")
            .setTags("test")
            .setHost("bmh-hv-6")
            .setContainerImage("fizzed/buildx:armhf-ubuntu16-jdk11"),

        new Target("linux", "armel", "Debian 11, JDK 11")
            .setTags("test")
            .setHost("bmh-hv-6")
            .setContainerImage("fizzed/buildx:armel-debian11-jdk11"),

        new Target("linux", "riscv64", "debian11")
            .setTags("test")
            .setHost("bmh-build-riscv64-debian11-1"),

        //
        // CI/Test Linux (w/ MUSL)
        //

        new Target("linux_musl", "x64", "Alpine 3.11, JDK 11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-alpine3.11-jdk11"),

        //
        // CI/Test MacOS
        //

        new Target("macos", "arm64", "MacOS 12")
            .setTags("test")
            .setHost("bmh-build-arm64-macos12-1"),

        //
        // CI/Test FreeBSD
        //

        new Target("freebsd", "x64", "FreeBSD 13")
            .setTags("test")
            .setHost("bmh-build-x64-freebsd13-1"),

        //
        // CI/Test Windows
        //

        new Target("windows", "x64", "Windows 10")
            .setTags("test")
            .setHost("bmh-build-x64-win10-1"),

        new Target("windows", "x64", "Windows 7")
            .setTags("test")
            .setHost("bmh-build-x64-win7-1"),

        new Target("windows", "arm64", "Windows 11")
            .setTags("test")
            .setHost("bmh-build-arm64-win11-1")

    );

    @Task(order=50)
    public void cross_list_targets() throws Exception {
        new Buildx(crossTargets)
            .listTargets();
    }

    @Task(order=51)
    public void cross_build_containers() throws Exception {
        new Buildx(crossTargets)
            .containersOnly()
            .execute((target, project) -> {
                // no customization needed
                project.buildContainer(new ContainerBuilder()
                    //.setCache(false)
                );
            });
    }

    @Task(order=52)
    public void cross_build_natives() throws Exception {
        new Buildx(crossTargets)
            .tags("build")
            .execute((target, project) -> {
                // NOTE: rust uses its cache heavily, causing issues when doing batch builds across architectures
                // its important we do a clean before we build
                project.action("java", "-jar", "blaze.jar", "clean_natives", "build_natives", "--target", target.getOsArch()).run();

                // we know that the only modified file will be in the artifact dir
                final String artifactRelPath = "shmemj-" + target.getOsArch() + "/src/main/resources/jne/" + target.getOs() + "/" + target.getArch() + "/";
                project.rsync(artifactRelPath, artifactRelPath).run();
            });
    }

    @Task(order=53)
    public void cross_tests() throws Exception {
        new Buildx(crossTargets)
            .tags("test")
            .execute((target, project) -> {
                project.action("java", "-jar", "blaze.jar", "test").run();
            });
    }

    @Task(order=54)
    public void readme_markdown() throws Exception {

        System.out.println();

        // generate list of artifacts built
        final Map<String,Target> buildTargets = new TreeMap<>();
        crossTargets.stream()
            .filter(v -> !v.getDescription().contains("local"))
            .filter(v -> v.getTags().contains("build"))
            .forEach(v -> buildTargets.put(v.getOsArch(), v));

        System.out.println("| Platform | Artifact | Notes |");
        System.out.println("| :--------------- | :----------- | :---- |");
        for (Target target : buildTargets.values()) {
            System.out.println("| " + target.getOs() + " " + target.getArch() + " | " + "shmemj-"+target.getOsArch() + " | " + target.getDescription() + " |");
        }

        System.out.println();
        System.out.println();

        // generate list of tests run
        final List<Target> testTargets = crossTargets.stream()
            .filter(v -> !v.getDescription().contains("local"))
            .filter(v -> v.getTags().contains("test"))
            .sorted((a, b) -> {
                int c = a.getOs().compareTo(b.getOs());
                if (c == 0) {
                    c = a.getArch().compareTo(b.getArch());
                    if (c == 0) {
                        c = a.getDescription().compareTo(b.getDescription());
                    }
                }
                return c;
            })
            .collect(toList());

        System.out.println("| Operating System | Architecture | Notes |");
        System.out.println("| :--------------- | :----------- | :---- |");
        for (Target target : testTargets) {
            System.out.println("| " + target.getOs() + " | " + target.getArch() + " | " + target.getDescription() + " |");
        }
    }

}
