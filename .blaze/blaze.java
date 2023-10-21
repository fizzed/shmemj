import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.util.Globber;
import com.fizzed.buildx.Buildx;
import com.fizzed.buildx.Target;
import com.fizzed.jne.HardwareArchitecture;
import com.fizzed.jne.LinuxLibC;
import com.fizzed.jne.OperatingSystem;
import com.fizzed.jne.PlatformInfo;
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

    public void clean_natives() throws Exception {
        final Path rustProjectDir = withBaseDir("../native");
        exec("cargo", "clean")
            .workingDir(rustProjectDir)
            .run();
    }

    public void build_natives() throws Exception {
        String os_str = Contexts.config().value("build-os").orNull();
        String arch_str = Contexts.config().value("build-arch").orNull();
        String libc_str = null;

        if (os_str.endsWith("_musl")) {
            os_str = os_str.replace("_musl", "");
            libc_str = "musl";
        }

        OperatingSystem os = PlatformInfo.detectOperatingSystem();
        HardwareArchitecture arch = PlatformInfo.detectHardwareArchitecture();
        LinuxLibC linuxLibC = PlatformInfo.detectLinuxLibC();

        if (os_str != null) {
            os = OperatingSystem.valueOf(os_str.toUpperCase());
        }

        if (arch_str != null) {
            arch = HardwareArchitecture.valueOf(arch_str.toUpperCase());
        }

        if (libc_str != null) {
            linuxLibC = LinuxLibC.valueOf(libc_str.toUpperCase());
        }

        os_str = os.name().toLowerCase();
        arch_str = arch.name().toLowerCase();
        libc_str = linuxLibC.name().toLowerCase();

        log.info("=================================================");
        log.info("");
        log.info("NOTE: you can target a specific os/arch by passing in --build-os <os> or --build-arch <arch>");
        log.info("on the command line when you run this script.");
        log.info("");
        log.info("Building for:");
        log.info("  operating system: {}", os);
        log.info("  hardware arch: {}", arch);
        log.info("  linux libc: {}", linuxLibC);

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
                rustArch = "riscv64gc";
                break;
        }

        String rustOs = null;
        switch (os) {
            case WINDOWS:
                rustOs = "pc-windows-msvc";
                break;
            case LINUX:
                if (linuxLibC == LinuxLibC.MUSL) {
                    rustOs = "unknown-linux-musl";
                } else {
                    rustOs = "unknown-linux-gnu";
                }
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
    }

    private final List<Target> crossTargets = asList(
        // Linux x64 (ubuntu 16.04, glibc 2.?)
        new Target("linux", "x64")
            .setTags("build", "test")
            .setContainerImage("fizzed/buildx:amd64-ubuntu16-jdk11-buildx-linux-x64"),

        // Linux arm64 (ubuntu 16.04, glibc 2.?)
        new Target("linux", "arm64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu16-jdk11-buildx-linux-arm64"),

        // Linux riscv64 (ubuntu 18.04, glibc 2.?)
        new Target("linux", "riscv64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu18-jdk11-buildx-linux-riscv64"),

        // Linux MUSL x64 (alpine 3.11)
        new Target("linux_musl", "x64")
            .setTags("build")
            .setContainerImage("fizzed/buildx:amd64-ubuntu16-jdk11-buildx-linux-musl-x64"),

        // MacOS x64 (10.13+)
        new Target("macos", "x64")
            .setTags("build")
            .setHost("bmh-build-x64-macos1013-1"),

        // MacOS arm64 (??)
        new Target("macos", "arm64")
            .setTags("build")
            .setHost("bmh-build-x64-macos11-1"),

        // Windows x64 (win7+)
        new Target("windows", "x64")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),

        // Windows x32 (win7+)
        new Target("windows", "x32")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),

        // Windows arm64 (win10+)
        new Target("windows", "arm64")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),






        new Target("linux", "arm64-test")
            .setTags("test")
            .setHost("bmh-build-arm64-ubuntu22-1"),
        //.setContainerImage("fizzed/buildx:arm64v8-ubuntu18-jdk11"),

        new Target("windows", "x64-test", "win10")
            .setTags("test")
            .setHost("bmh-build-x64-win10-1"),

        new Target("windows", "x64-test", "win7")
            .setTags("test")
            .setHost("bmh-build-x64-win7-1")


        /*
        // MacOS arm64 (12+)
        new Target("macos", "arm64")
            .setTags("build", "test")
            .setHost("bmh-build-arm64-macos12-1"),



        // Windows arm64 (win10+)
        new Target("windows", "arm64")
            .setTags("build")
            .setHost("bmh-build-x64-win11-1"),*/

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
        new Buildx(crossTargets)
            .execute((target, project) -> {
                if (project.hasContainer()) {
                    project.exec("setup/build-docker-container-action.sh", target.getContainerImage(), project.getContainerName(), target.getOs(), target.getArch()).run();
                }
            });
    }

    public void cross_build_natives() throws Exception {
        new Buildx(crossTargets)
            .setTags("build")
            .execute((target, project) -> {

                /*String buildScript = "setup/build-native-lib-linux-action2.sh";
                project.action(buildScript, target.getOs(), target.getArch()).run();*/

                String buildScript = "setup/blaze-action.sh";
                if (target.getOs().equals("windows")) {
                    buildScript = "setup/blaze-action.bat";
                }

                // i know its nuts but this will invoke a task within this file
                project.action(buildScript, "build_natives", "--build-os", target.getOs(), "--build-arch", target.getArch()).run();

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

    public void cross_run_tests() throws Exception {
        new Buildx(crossTargets)
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
