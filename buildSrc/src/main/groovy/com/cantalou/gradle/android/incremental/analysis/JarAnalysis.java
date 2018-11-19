package com.cantalou.gradle.android.incremental.analysis;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author cantalou
 * @date 2018-11-18 11:36
 */
public class JarAnalysis extends AbstractAnalysis<List<File>> {

    private File preJarFile;

    private File currentJarFile;

    public JarAnalysis(List<File> preCompileResource, List<File> currentCompileResource, File currentJarFile) throws MalformedURLException {
        super(preCompileResource, currentCompileResource);
        this.currentJarFile = currentJarFile;
        String jarFileName = Math.abs(currentJarFile.hashCode()) + "-" + currentJarFile.getName();
        for (File file : preCompileResource) {
            if (jarFileName.equals(file.getName())) {
                preJarFile = file;
                break;
            }
        }
    }

    @Override
    public void analysis() throws Exception {
        if (preJarFile == null || !preJarFile.exists()) {
            setFullRebuildCause("can not find precompile classpath jar for " + currentJarFile);
            return;
        }
        URLClassLoader preCl = createClassLoader(preCompileResource);
        URLClassLoader currentCl = createClassLoader(currentCompileResource);
        ZipFile preZipFile = new ZipFile(preJarFile);
        ZipFile currentZipFile = new ZipFile(currentJarFile);
        Enumeration<? extends ZipEntry> e = preZipFile.entries();
        while (e.hasMoreElements()) {
            ZipEntry preZipEntry = e.nextElement();
            ZipEntry currentZipEntry = currentZipFile.getEntry(preZipEntry.getName());
            if (preZipEntry.getCrc() == currentZipEntry.getCrc()) {
                continue;
            }
            String className = preZipEntry.getName()
                                          .replace('/', '.')
                                          .replace(".class", "");
            Class preClass = preCl.loadClass(className);
            Class currentClass = currentCl.loadClass(className);
            ClassAnalysis ca = new ClassAnalysis(preClass, currentClass);
            ca.analysis();
            if (ca.isFullRebuildNeeded()) {
                setFullRebuildCause(ca.getFullRebuildCause());
                break;
            }
        }
        preZipFile.close();
        currentZipFile.close();
    }

    private URLClassLoader createClassLoader(List<File> jarFiles) throws MalformedURLException {
        URL[] urls = new URL[jarFiles.size()];
        for (int i = 0; i < jarFiles.size(); i++) {
            urls[i] = jarFiles.get(i)
                              .toURL();
        }
        return new URLClassLoader(urls);
    }
}
