import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Task;
import com.fizzed.blaze.util.Globber;
import com.fizzed.buildx.Buildx;
import com.fizzed.buildx.ContainerBuilder;
import com.fizzed.buildx.Target;
import com.fizzed.jne.NativeTarget;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.Systems.exec;
import static java.util.Arrays.asList;

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
            .run();

        for (String ext : asList(".so", ".dll", ".dylib")) {
            for (Path f : Globber.globber(rustArtifactDir, "*"+ext).filesOnly().scan()) {
                log.info("Copying {} -> {}", f, javaOutputDir);
                Files.copy(f, javaOutputDir.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Task(order=20)
    public void clean_natives() throws Exception {
        exec("cargo", "clean")
            .workingDir(rustProjectDir)
            .run();
    }

    @Task(order=30)
    public void test() throws Exception {
        exec("mvn", "clean", "test")
            .workingDir(projectDir)
            .run();
    }

    private final List<Target> crossTargets = asList(
        //
        // Linux GNU
        //

        new Target("linux", "x64", "ubuntu16.04, jdk11")
            .setTags("build", "test")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-x64"),

        new Target("linux", "arm64", "ubuntu16.04, jdk11")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-arm64"),

        // riscv64 only on ubuntu18+
        new Target("linux", "riscv64", "ubuntu18.04, jdk11")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-riscv64"),

        new Target("linux", "armhf", "ubuntu16.04, jdk11")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-armhf"),

        new Target("linux", "armel", "ubuntu16.04, jdk11")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-armel"),

        new Target("linux", "x32", "ubuntu16.04, jdk11")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-x32"),

        //
        // Linux MUSL
        //

        new Target("linux_musl", "x64", "ubuntu16.04, jdk11")
            .setTags("build")
            .setContainerImage("fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux_musl-x64"),

        //
        // MacOS
        //

        new Target("macos", "x64", "macos10.13")
            .setTags("build", "test")
            .setHost("bmh-build-x64-macos1013-1"),

        new Target("macos", "arm64", "macos11")
            .setTags("build")
            .setHost("bmh-build-x64-macos11-1"),

        //
        // Windows
        //

        new Target("windows", "x64", "win11")
            .setTags("build", "test")
            .setHost("bmh-build-x64-win11-1"),

        new Target("windows", "x32", "win11")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),

        new Target("windows", "arm64", "win11")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),

        //
        // FreeBSD
        //

        new Target("freebsd", "x64", "freebsd12")
            .setTags("build", "test")
            .setHost("bmh-build-x64-freebsd12-1"),

        //
        // Testing Only
        //

        new Target(localNativeTarget.toJneOsAbi(), localNativeTarget.toJneArch(), "local machine")
            .setTags("build", "dude", "test"),

        new Target("linux", "x64", "ubuntu22.04, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk11"),

        new Target("linux", "x64", "ubuntu22.04, jdk17")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk17"),

        new Target("linux", "x64", "ubuntu22.04, jdk21")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk21"),

        new Target("linux", "x32", "ubuntu16.04, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x32-ubuntu16-jdk11"),

        new Target("linux", "arm64", "ubuntu16.04, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:arm64-ubuntu16-jdk11"),

        new Target("linux", "armhf", "ubuntu16.04, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:armhf-ubuntu16-jdk11"),

        new Target("linux", "armel", "debian11, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:armel-debian11-jdk11"),

        new Target("linux_musl", "x64", "alpine3.11, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-alpine3.11-jdk11"),

        new Target("macos", "arm64", "macos12")
            .setTags("test")
            .setHost("bmh-build-arm64-macos12-1"),

        new Target("windows", "x64", "win10")
            .setTags("test")
            .setHost("bmh-build-x64-win10-1"),

        new Target("windows", "x64", "win7")
            .setTags("test")
            .setHost("bmh-build-x64-win7-1"),

        new Target("windows", "arm64", "win11")
            .setTags("test")
            .setHost("bmh-build-arm64-win11-1"),

        new Target("linux", "riscv64", "debian11")
            .setTags("test")
            .setHost("bmh-build-riscv64-debian11-1")
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

}
