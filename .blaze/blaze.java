import com.fizzed.blaze.util.Globber;
import com.fizzed.jne.HardwareArchitecture;
import com.fizzed.jne.OperatingSystem;
import com.fizzed.jne.PlatformInfo;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import static java.util.Arrays.asList;
import java.nio.file.Path;
import java.util.function.Supplier;
import static java.util.stream.Collectors.toList;
import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.system.Exec;
import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.Contexts.fail;
import com.fizzed.blaze.Systems;
import static com.fizzed.blaze.Systems.exec;
import com.fizzed.blaze.ssh.SshSession;
import static com.fizzed.blaze.SecureShells.sshConnect;
import static com.fizzed.blaze.SecureShells.sshExec;
import com.fizzed.buildx.*;

public class blaze {
    final private Logger log = Contexts.logger();

    private final List<Target> targets = asList(
        // Linux x64 (ubuntu 16.04, glibc 2.?)
        new Target("linux", "x64")
            .setTags("build", "test")
            .setContainerImage("fizzed/buildx:amd64-ubuntu16-jdk11-cross-build"),

        /*
        // Linux arm64 (ubuntu 18.04, glibc 2.27+)
        new Target("linux", "arm64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-cross-build"),

        // Linux armhf (ubuntu 18.04, glibc 2.27+)
        new Target("linux", "armhf")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-cross-build"),

        // Linux armel (ubuntu 18.04, glibc 2.27+)
        new Target("linux", "armel")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-cross-build"),

        // Linux MUSL x64 (alpine 3.11)
        new Target("linux_musl", "x64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-cross-build"),

        // Linux MUSL arm64 (alpine 3.11)
        new Target("linux_musl", "arm64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-cross-build"),

        // Linux riscv64 (ubuntu 20.04, glibc 2.31+)
        new Target("linux", "riscv64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-cross-build"),
        */

        // MacOS x64 (10.13+)
        new Target("macos", "x64")
            .setTags("build", "test")
            .setHost("bmh-build-x64-macos1013-1"),

        // Windows x64 (win7+)
        new Target("windows", "x64")
            .setTags("build", "test")
            .setHost("bmh-build-x64-win11-1"),

        /*
        // MacOS arm64 (12+)
        new Target("macos", "arm64")
            .setTags("build", "test")
            .setHost("bmh-build-arm64-macos12-1"),



        // Windows arm64 (win10+)
        new Target("windows", "arm64")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),*/

        //
        // test-only containers
        //

        /*
        new Target("linux", "arm64-test")
            .setTags("test")
            //.setHost("bmh-build-arm64-ubuntu22-1")
            .setContainerImage("fizzed/buildx:arm64v8-ubuntu18-jdk11"),

        new Target("linux", "armhf-test")
            .setTags("test")
            .setContainerImage("fizzed/buildx:arm32v7-ubuntu18-jdk11"),

        new Target("linux", "armel-test")
            .setTags("test")
            .setContainerImage("fizzed/buildx:arm32v5-debian11-jdk11"),

        new Target("linux", "armel-test")
            .setTags("test")
            .setContainerImage("fizzed/buildx:arm32v5-debian11-jdk11"),

        new Target("linux", "riscv64-test")
            .setTags("test")
            .setContainerImage("fizzed/buildx:riscv64-ubuntu20-jdk19"),

        new Target("linux_musl", "x64-test")
            .setTags("test")
            .setContainerImage("fizzed/buildx:amd64-alpine3.11-jdk11"),

        new Target("linux_musl", "arm64-test")
            .setTags("test")
            //.setHost("bmh-build-arm64-ubuntu22-1")
            .setContainerImage("fizzed/buildx:arm64v8-alpine3.11-jdk11"),*/

        new Target("windows", "x64-test", "win10")
            .setTags("build", "test")
            .setHost("bmh-build-x64-win10-1"),

        new Target("windows", "x64-test", "win7")
            .setTags("test")
            .setHost("bmh-build-x64-win7-1")

        /*new Target("windows", "arm64-test", "win11")
            .setTags("test")
            .setHost("bmh-build-arm64-win11-1")*/
    );

    public void build_containers() throws Exception {
        new Buildx(targets)
            .execute((target, project) -> {
                if (project.hasContainer()) {
                    project.exec("setup/build-docker-container-action.sh", target.getContainerImage(), project.getContainerName(), target.getOs(), target.getArch()).run();
                }
            });
    }

    public void build_native_libs() throws Exception {
        String os_str = Contexts.config().value("build-os").orNull();
        String arch_str = Contexts.config().value("build-arch").orNull();

        OperatingSystem os = PlatformInfo.detectOperatingSystem();
        HardwareArchitecture arch = PlatformInfo.detectHardwareArchitecture();

        if (os_str != null) {
            os = OperatingSystem.valueOf(os_str.toUpperCase());
        }

        if (arch_str != null) {
            arch = HardwareArchitecture.valueOf(arch_str.toUpperCase());
        }

        os_str = os.name().toLowerCase();
        arch_str = arch.name().toLowerCase();

        log.info("=================================================");
        log.info("");
        log.info("NOTE: you can target a specific os/arch by passing in --build-os <os> or --build-arch <arch>");
        log.info("on the command line when you run this script.");
        log.info("");
        log.info("Building for:");
        log.info("  operating system: {}", os);
        log.info("  hardware arch: {}", arch);

        // rust target from os-arch
        // https://doc.rust-lang.org/nightly/rustc/platform-support.html
        String rustArch = null;
        switch (arch) {
            case X64:
                rustArch = "x86_64";
                break;
            case X32:
                rustArch = "i686";
                break;
            case ARM64:
                rustArch = "aarch64";
                break;
            case RISCV64:
                rustArch = "riscv64";
                break;
        }

        String rustOs = null;
        switch (os) {
            case WINDOWS:
                rustOs = "pc-windows-msvc";
                break;
            case LINUX:
                rustOs = "unknown-linux-gnu";
                break;
            case MACOS:
                rustOs = "apple-darwin";
                break;
            case FREEBSD:
                rustOs = "unknown-freebsd";
                break;
            case OPENBSD:
                rustOs = "unknown-openbsd";
                break;
        }

        final String rustTarget = rustArch + "-" + rustOs;

        final Path rustProjectDir = withBaseDir("../native");
        final Path rustArtifactDir = rustProjectDir.resolve("target/"+rustTarget+"/release");
        final Path javaOutputDir = withBaseDir("../shmemj-"+os_str+"-"+arch_str+"/src/main/resources/jne/"+os_str+"/"+arch_str);

        log.info("  rustTarget: {}", rustTarget);
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

        /*$input_dir = "$project_dir\native\target\$target\release"
        $output_dir = "$project_dir\shmemj-$build_os-$build_arch\src\main\resources\jne\${build_os}\${build_arch}"
        Write-Output "Copying $input_dir\*.dll -> $output_dir"*/

        /*new Buildx(targets)
            .setTags("build")
            .execute((target, project) -> {
                String buildScript = "setup/build-native-lib-linux-action.sh";
                if (target.getOs().equals("macos")) {
                    buildScript = "setup/build-native-lib-macos-action.sh";
                } else if (target.getOs().equals("windows")) {
                    buildScript = "setup/build-native-lib-windows-action.bat";
                }

                project.action(buildScript, target.getOs(), target.getArch()).run();

                // we know that the only modified file will be in the artifact dir
                final String artifactRelPath = "tkrzw-" + target.getOsArch() + "/src/main/resources/jne/" + target.getOs() + "/" + target.getArch() + "/";
                project.rsync(artifactRelPath, artifactRelPath).run();
            });*/
    }

    public void batch_build_native_libs() throws Exception {
        new Buildx(targets)
            .setTags("build")
            .execute((target, project) -> {
                String buildScript = "setup/blaze-action.sh";
                if (target.getOs().equals("windows")) {
                    buildScript = "setup/blaze-action.bat";
                }

                // i know its nuts but this will invoke a task within this file
                project.action(buildScript, "build_native_libs", "--build-os", target.getOs(), "--build-arch", target.getArch()).run();

                /*String buildScript = "setup/build-native-lib-linux-action.sh";
                if (target.getOs().equals("macos")) {
                    buildScript = "setup/build-native-lib-macos-action.sh";
                } else if (target.getOs().equals("windows")) {
                    buildScript = "setup/build-native-lib-windows-action.bat";
                }

                project.action(buildScript, target.getOs(), target.getArch()).run();*/

                // we know that the only modified file will be in the artifact dir
                final String artifactRelPath = "shmemj-" + target.getOsArch() + "/src/main/resources/jne/" + target.getOs() + "/" + target.getArch() + "/";
                project.rsync(artifactRelPath, artifactRelPath).run();
            });
    }

    public void tests() throws Exception {
        new Buildx(targets)
            .setTags("test")
            .execute((target, project) -> {
                String testScript = "setup/test-project-action.sh";
                if (target.getOs().equals("windows")) {
                    testScript = "setup/test-project-action.bat";
                }

                project.action(testScript).run();
            });
    }

}
