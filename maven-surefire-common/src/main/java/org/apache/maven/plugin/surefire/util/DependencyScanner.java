package org.apache.maven.plugin.surefire.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.apache.maven.plugin.surefire.util.ScannerUtil.convertJarFileResourceToJavaClassName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.surefire.testset.TestFilter;
import org.apache.maven.surefire.testset.TestListResolver;
import org.apache.maven.surefire.util.DefaultScanResult;

/**
 * Scans dependencies looking for tests.
 *
 * @author Aslak Knutsen
 */
public class DependencyScanner
{
    private final List<File> dependenciesToScan;

    private final TestListResolver filter;

    public DependencyScanner( List<File> dependenciesToScan, TestListResolver filter )
    {
        this.dependenciesToScan = dependenciesToScan;
        this.filter = filter;
    }

    public DefaultScanResult scan()
        throws MojoExecutionException
    {
        Set<String> classes = new LinkedHashSet<String>();
        for ( File artifact : dependenciesToScan )
        {
            try
            {
                scanArtifact( artifact, filter, classes );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Could not scan dependency " + artifact.toString(), e );
            }
        }
        return new DefaultScanResult( new ArrayList<String>( classes ) );
    }

    private static void scanArtifact( File artifact, TestFilter<String, String> filter, Set<String> classes )
        throws IOException
    {
        if ( artifact != null && artifact.isFile() )
        {
            JarFile jar = null;
            try
            {
                jar = new JarFile( artifact );
                for ( Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); )
                {
                    JarEntry entry = entries.nextElement();
                    if ( filter.shouldRun( entry.getName(), null ) )
                    {
                        classes.add( convertJarFileResourceToJavaClassName( entry.getName() ) );
                    }
                }
            }
            finally
            {
                if ( jar != null )
                {
                    jar.close();
                }
            }
        }
    }

    /**
     * Filters <code>artifacts</code> using the <code>groupArtifactIds</code> passed in, and returns a list of
     * {@link Artifact#getFile()}s of the artifacts matching the filter. If none match, and empty list is returned. The
     * format of the <code>groupArtifactIds</code> strings passed in should be:
     * <pre>groupId:artifactId[:packaging/type[:classifier[:version]]]</pre>
     */
    public static List<File> filter( List<Artifact> artifacts, List<String> groupArtifactIds )
    {
        List<File> matches = new ArrayList<File>();
        if ( groupArtifactIds == null || artifacts == null )
        {
            return matches;
        }
        for ( Artifact artifact : artifacts )
        {
            for ( String groups : groupArtifactIds )
            {
                if ( !hasGroupAndArtifactId( groups ) )
                {
                    throw new IllegalArgumentException( "dependencyToScan argument should be in format"
                        + " 'groupId:artifactId[:packaging/type[:classifier[:version]]]': " + groups );
                }
                if ( artifactMatchesGavtc( artifact, groups ) )
                {
                    matches.add( artifact.getFile() );
                }
            }
        }
        return matches;
    }
    
    private static boolean artifactMatchesGavtc( Artifact artifact, String groups )
    {
        String[] gavtc = StringUtils.splitPreserveAllTokens( groups, ':' );
        boolean match = artifact.getGroupId().matches( gavtc[0] ) && artifact.getArtifactId().matches( gavtc[1] );

        match = match && ( !hasVersion( gavtc ) || artifact.getVersion().equals( gavtc[2] ) );

        match =
            match && ( !hasType( gavtc ) || ( artifact.getType() != null && artifact.getType().equals( gavtc[3] ) ) );

        match = match && ( !hasClassifier( gavtc )
            || ( artifact.getClassifier() != null && artifact.getClassifier().matches( gavtc[4] ) ) );

        return match;
    }
    
    private static boolean hasGroupAndArtifactId( String groups )
    {
        return StringUtils.countMatches( groups, ':' ) >= 1;
    }
    
    private static boolean hasVersion( String... groups )
    {
        return groups.length >= 3 && StringUtils.isNotBlank( groups[2] );
    }
    
    private static boolean hasType( String... groups )
    {
        return groups.length >= 4 && StringUtils.isNotBlank( groups[3] );
    }
    
    private static boolean hasClassifier( String... groups )
    {
        return groups.length >= 5 && StringUtils.isNotBlank( groups[4] );
    }
}
