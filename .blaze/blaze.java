import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.system.Exec;
import com.fizzed.blaze.util.Globber;
import com.fizzed.buildx.Buildx;
import com.fizzed.buildx.Target;
import com.fizzed.jne.*;
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

    public void test() throws Exception {
        exec("mvn", "clean", "test")
            .workingDir(projectDir)
            .run();
    }

    public void clean_natives() throws Exception {
        exec("cargo", "clean")
            .workingDir(rustProjectDir)
            .run();
    }

    public void build_natives() throws Exception {
        final String targetStr = Contexts.config().value("target").orNull();
        final NativeTarget nativeTarget = targetStr != null ? NativeTarget.fromJneTarget(targetStr) : NativeTarget.detect();
        final String jneTarget = nativeTarget.toJneTarget();
        final String rustTarget = nativeTarget.toRustTarget();

        final Path rustProjectDir = withBaseDir("../native");
        final Path rustArtifactDir = rustProjectDir.resolve("target/" + rustTarget + "/release");
        final Path javaOutputDir = withBaseDir("../shmemj-" + jneTarget + "/src/main/resources/jne/" + nativeTarget.toJneOsAbi() + "/" + nativeTarget.toJneArch());

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

        new Target("linux", "x64", "ubuntu22.04, jdk11")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk11"),

        new Target("linux", "x64", "ubuntu22.04, jdk17")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk17"),

        new Target("linux", "x64", "ubuntu22.04, jdk21")
            .setTags("test")
            .setContainerImage("fizzed/buildx:x64-ubuntu22-jdk21"),

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

        /*
        // MacOS arm64 (12+)
        new Target("macos", "arm64")
            .setTags("build", "test")
            .setHost("bmh-build-arm64-macos12-1"),



        // Windows arm64 (win10+)
        ,*/

    );

    public void cross_targets() throws Exception {
        log.info("Build Targets:");
        new Buildx(crossTargets).getTargets().forEach(target -> {
            if (target.getTags().contains("build")) {
                log.info("  {}", target.getOsArch());
                if (target.getContainerImage() != null) {
                    log.info("    container: {}", target.getContainerImage());
                }
            }
        });
        log.info("Test Targets:");
        new Buildx(crossTargets).getTargets().forEach(target -> {
            if (target.getTags().contains("test")) {
                log.info("  {}", target.getOsArch());
            }
        });
    }

    public void cross_build_containers() throws Exception {
        final String user = System.getenv("USER");
        final String userId = exec("id", "-u", user).runCaptureOutput().toString();
        new Buildx(crossTargets)
            .onlyWithContainers()
            .execute((target, project) -> {
                // we need a temp .m2 and .ivy2
                Files.createDirectories(projectDir.resolve(".buildx-temp/m2"));
                Files.createDirectories(projectDir.resolve(".buildx-temp/ivy2"));

                String dockerFile = "setup/Dockerfile.linux";
                if (target.getContainerImage().contains("alpine")) {
                    dockerFile = "setup/Dockerfile.linux_musl";
                }

                project.exec("docker", "build",
                        "-f", dockerFile,
                        "--build-arg", "FROM_IMAGE="+target.getContainerImage(),
                        "--build-arg", "USERID="+userId,
                        "--build-arg", "USERNAME="+user,
                        "-t", project.getContainerName(),
                        "setup")
                    .run();
            });
    }

    public void cross_build_natives() throws Exception {
        new Buildx(crossTargets)
            .setTags("build")
            .execute((target, project) -> {
                // NOTE: rust uses its cache heavily, causing issues when doing batch builds across architectures
                // its important we do a clean before we build
                project.action("java", "-jar", "blaze.jar", "clean_natives", "build_natives", "--target", target.getOsArch()).run();

                // we know that the only modified file will be in the artifact dir
                final String artifactRelPath = "shmemj-" + target.getOsArch() + "/src/main/resources/jne/" + target.getOs() + "/" + target.getArch() + "/";
                project.rsync(artifactRelPath, artifactRelPath).run();
            });
    }

    public void test_shell() throws Exception {
        new Buildx(crossTargets)
            .setTags("build")
            .execute((target, project) -> {
                project.getSshSession().newExec()
                    .command("/Users/builder/remote-build/shmemj/setup/test-exec.sh")
                    .args("java", "-version")
                    .run();
            });
    }

    public void cross_tests() throws Exception {
        new Buildx(crossTargets)
            .setTags("test")
            .execute((target, project) -> {
//                project.action("java", "-jar", "blaze.jar", "test").run();
                project.action("java", "-jar", "blaze.jar", "test").run();
            });
    }

}
