package com.behsa.ganjex.e2e;

import com.behsa.ganjex.bootstrap.Bootstrap;
import com.behsa.ganjex.config.Config;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Esa Hekmatizadeh
 */
public class TestUtil {
	public static final String TEST_PATH = "src/test/java/com/behsa/ganjex/e2e/";
	public static final String TEST_CONFIG_PATH = "src/test/resources/config-test.properties";
	private static final Logger log = LoggerFactory.getLogger(TestUtil.class);

	public static void clean() throws IOException {
		File testDist = new File("test-dist");
		FileUtils.deleteDirectory(testDist);
		Config.setConfig(new TestConfiguration());
		File tmp = new File("tmp");
		FileUtils.deleteDirectory(tmp);
	}

	public static void deployService(String path, String name) throws IOException, InterruptedException {
		File tmpSrc = new File("tmp/" + name + "/src");
		File tmpOut = new File("tmp/" + name + "/out");
		copyAndCompile("service", path, tmpSrc, tmpOut);
		commandRun("jar -cf " + name + ".jar -C out/ .", tmpOut.getParentFile());
		FileUtils.copyFileToDirectory(new File(tmpOut.getParent(), name + ".jar"),
						new File(Config.config().get("service.path")));
	}


	public static void deployLib(String path, String name) throws IOException, InterruptedException {
		File tmpSrc = new File("tmp/" + name + "/src");
		File tmpOut = new File("tmp/" + name + "/out");
		copyAndCompile("lib", path, tmpSrc, tmpOut);
		commandRun("jar -cf " + name + ".jar -C out/ .", tmpOut.getParentFile());
		FileUtils.copyFileToDirectory(new File(tmpOut.getParentFile(), name + ".jar"),
						new File(Config.config().get("lib.path")));
	}

	public static void waitToBootstrap() throws InterruptedException {
		while (!Bootstrap.bootstraped())
			Thread.sleep(500);
	}

	public static <T> T invokeStaticMethod(String className, String methodName, Class<T> returnType,
																				 Class<?>[] argType, Object... args)
					throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
					IllegalAccessException {
		Class<?> clazz = Bootstrap.libClassLoader().loadClass(className);
		Method method = clazz.getMethod(methodName, argType);
		return returnType.cast(method.invoke(null, args));
	}

	private static void copyAndCompile(String extType, String srcPath, File destFile, File outFile)
					throws IOException {
		if (!outFile.mkdirs())
			log.error("could not make directory {}", outFile);
		copyAllContent(new File(srcPath), destFile);
		deleteAllFilesInDirectory(destFile, new String[]{"java", "class", "jar"});
		Collection<File> libFiles = FileUtils.listFiles(destFile, new String[]{extType}, true);
		libFiles.forEach(file -> {
			if (!file.renameTo(new File(file.getAbsolutePath()
							.substring(0, file.getAbsolutePath().length() - (extType.length() + 1)) + ".java")))
				log.error("can not rename file: {}", file.getAbsolutePath());
		});
		Collection<File> sourceFiles = FileUtils.listFiles(destFile, new String[]{"java"}, true);
		List<String> args = new ArrayList<>();
		args.add("-d");
		args.add(outFile.getPath());
		args.addAll(sourceFiles.stream().map(File::getPath).collect(Collectors.toList()));
		ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(new String[0]));
		copyAllContent(destFile, outFile);
		deleteAllFilesInDirectory(destFile, new String[]{"java"});
	}

	private static void deleteAllFilesInDirectory(File dir, String[] extentions) {
		Collection<File> filesToDelete =
						FileUtils.listFiles(dir, extentions, true);
		filesToDelete.forEach(f -> {
			if (!f.delete())
				log.error("could not delete file {}", f.getAbsolutePath());
		});
	}

	private static void copyAllContent(File src, File dest) throws IOException {
		File[] allSourceFiles = src.listFiles();
		if (Objects.isNull(allSourceFiles))
			throw new IllegalArgumentException("srcPath is not correct: " + src.getPath());
		for (File f : allSourceFiles) {
			if (f.isDirectory())
				FileUtils.copyDirectoryToDirectory(f, dest);
			else
				FileUtils.copyFileToDirectory(f, dest);
		}
	}

	private static void commandRun(String command, File workDir)
					throws IOException, InterruptedException {
		CommandLine cm = CommandLine.parse(command);
		DefaultExecutor executor = new DefaultExecutor();
		executor.setWorkingDirectory(workDir);
		DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler();
		executor.execute(cm, handler);
		handler.waitFor();
	}

}