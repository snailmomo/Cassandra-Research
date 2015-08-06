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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.io.sstable;

import java.io.File;
import java.util.*;

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.Directories;
import org.apache.cassandra.io.sstable.format.SSTableFormat;
import org.apache.cassandra.io.sstable.format.Version;
import org.apache.cassandra.io.sstable.metadata.IMetadataSerializer;
import org.apache.cassandra.io.sstable.metadata.LegacyMetadataSerializer;
import org.apache.cassandra.io.sstable.metadata.MetadataSerializer;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.utils.Pair;

import static org.apache.cassandra.io.sstable.Component.separator;

/**
 * A SSTable is described by the keyspace and column family it contains data
 * for, a generation (where higher generations contain more recent data) and
 * an alphabetic version string.
 *
 * A descriptor can be marked as temporary, which influences generated filenames.
 */
public class Descriptor
{
//<<<<<<< HEAD
//    public static enum Type
//    {
//        TEMP("tmp", true), TEMPLINK("tmplink", true), FINAL(null, false);
//        public final boolean isTemporary;
//        public final String marker;
//        Type(String marker, boolean isTemporary)
//        {
//            this.isTemporary = isTemporary;
//            this.marker = marker;
//        }
//    }
//
//
//=======
    public static String TMP_EXT = ".tmp";
    public final File directory;
    /** version has the following format: <code>[a-z]+</code> */
    public final Version version;
    public final String ksname;
    public final String cfname;
    public final int generation;
    public final SSTableFormat.Type formatType;
    private final int hashCode;

    /**
     * A descriptor that assumes CURRENT_VERSION.
     */
    public Descriptor(File directory, String ksname, String cfname, int generation)
    {
        this(DatabaseDescriptor.getSSTableFormat().info.getLatestVersion(), directory, ksname, cfname, generation, DatabaseDescriptor.getSSTableFormat());
    }

    public Descriptor(File directory, String ksname, String cfname, int generation, SSTableFormat.Type formatType)
    {
        this(formatType.info.getLatestVersion(), directory, ksname, cfname, generation, formatType);
    }

    public Descriptor(String version, File directory, String ksname, String cfname, int generation, SSTableFormat.Type formatType)
    {
        this(formatType.info.getVersion(version), directory, ksname, cfname, generation, formatType);
    }

    public Descriptor(Version version, File directory, String ksname, String cfname, int generation, SSTableFormat.Type formatType)
    {
        assert version != null && directory != null && ksname != null && cfname != null && formatType.info.getLatestVersion().getClass().equals(version.getClass());
        this.version = version;
        this.directory = directory;
        this.ksname = ksname;
        this.cfname = cfname;
        this.generation = generation;
        this.formatType = formatType;

        hashCode = Objects.hashCode(version, directory, generation, ksname, cfname, formatType);
    }

    public Descriptor withGeneration(int newGeneration)
    {
        return new Descriptor(version, directory, ksname, cfname, newGeneration, formatType);
    }

    public Descriptor withFormatType(SSTableFormat.Type newType)
    {
        return new Descriptor(newType.info.getLatestVersion(), directory, ksname, cfname, generation, newType);
    }

    public String tmpFilenameFor(Component component)
    {
        return filenameFor(component) + TMP_EXT;
    }

    public String filenameFor(Component component) //包含component
    {
        return filenameFor(component.name());
    }

    public String baseFilename() //不包含component
    {
        StringBuilder buff = new StringBuilder();
        buff.append(directory).append(File.separatorChar);
        appendFileName(buff);
        return buff.toString();
    }

    private void appendFileName(StringBuilder buff)
    {
        if (!version.hasNewFileName())
        {
            buff.append(ksname).append(separator);
            buff.append(cfname).append(separator);
        }
        buff.append(version).append(separator);
        buff.append(generation);
        if (formatType != SSTableFormat.Type.LEGACY)
            buff.append(separator).append(formatType.name);
    }

    public String relativeFilenameFor(Component component)
    {
        final StringBuilder buff = new StringBuilder();
        appendFileName(buff);
        buff.append(separator).append(component.name());
        return buff.toString();
    }

    public SSTableFormat getFormat()
    {
        return formatType.info;
    }

    /**
     * @param suffix A component suffix, such as 'Data.db'/'Index.db'/etc
     * @return A filename for this descriptor with the given suffix.
     */
    public String filenameFor(String suffix)
    {
        return baseFilename() + separator + suffix;
    }


    /** Return any temporary files found in the directory */
    public List<File> getTemporaryFiles()
    {
        List<File> ret = new ArrayList<>();
        File[] tmpFiles = directory.listFiles((dir, name) ->
                                              name.endsWith(Descriptor.TMP_EXT));

        for (File tmpFile : tmpFiles)
            ret.add(tmpFile);

        return ret;
    }

    /**
     *  Files obsoleted by CASSANDRA-7066 :
     *  - temporary files used to start with either tmp or tmplink
     *  - system.compactions_in_progress sstable files
     */
    public static boolean isLegacyFile(String fileName)
    {
        return fileName.startsWith("compactions_in_progress") ||
               fileName.startsWith("tmp") ||
               fileName.startsWith("tmplink");
    }

    public static boolean isValidFile(String fileName)
    {
        return fileName.endsWith(".db") && !isLegacyFile(fileName);
    }

    /**
     * @see #fromFilename(File directory, String name)
     * @param filename The SSTable filename
     * @return Descriptor of the SSTable initialized from filename
     */
    public static Descriptor fromFilename(String filename)
    {
        File file = new File(filename);
        return fromFilename(file.getParentFile(), file.getName(), false).left;
    }

    public static Descriptor fromFilename(String filename, SSTableFormat.Type formatType)
    {
        return fromFilename(filename).withFormatType(formatType);
    }

    public static Descriptor fromFilename(String filename, boolean skipComponent)
    {
        File file = new File(filename);
        return fromFilename(file.getParentFile(), file.getName(), skipComponent).left;
    }

    public static Pair<Descriptor, String> fromFilename(File directory, String name)
    {
        return fromFilename(directory, name, false);
    }

    /**
     * Filename of the form is vary by version:
     *
     * <ul>
     *     <li>&lt;ksname&gt;-&lt;cfname&gt;-(tmp-)?&lt;version&gt;-&lt;gen&gt;-&lt;component&gt; for cassandra 2.0 and before</li>
     *     <li>(&lt;tmp marker&gt;-)?&lt;version&gt;-&lt;gen&gt;-&lt;component&gt; for cassandra 3.0 and later</li>
     * </ul>
     *
     * If this is for SSTable of secondary index, directory should ends with index name for 2.1+.
     *
     * @param directory The directory of the SSTable files
     * @param name The name of the SSTable file
     * @param skipComponent true if the name param should not be parsed for a component tag
     *
     * @return A Descriptor for the SSTable, and the Component remainder.
     */
    //见Component.fromFilename(File, String)中的注释
    public static Pair<Descriptor, String> fromFilename(File directory, String name, boolean skipComponent)
    {
        File parentDirectory = directory != null ? directory : new File(".");

        // tokenize the filename
        StringTokenizer st = new StringTokenizer(name, String.valueOf(separator));
        String nexttok;

        // read tokens backwards to determine version
        Deque<String> tokenStack = new ArrayDeque<>();
        while (st.hasMoreTokens())
        {
            tokenStack.push(st.nextToken());
        }

        // component suffix
        String component = skipComponent ? null : tokenStack.pop();

        nexttok = tokenStack.pop();
        // generation OR format type
        SSTableFormat.Type fmt = SSTableFormat.Type.LEGACY;
        if (!CharMatcher.DIGIT.matchesAllOf(nexttok))
        {
            fmt = SSTableFormat.Type.validate(nexttok);
            nexttok = tokenStack.pop();
        }

        // generation
        int generation = Integer.parseInt(nexttok);

        // version
        nexttok = tokenStack.pop();
        Version version = fmt.info.getVersion(nexttok);

        if (!version.validate(nexttok))
            throw new UnsupportedOperationException("SSTable " + name + " is too old to open.  Upgrade to 2.0 first, and run upgradesstables");

        // ks/cf names
        String ksname, cfname;
        if (version.hasNewFileName())
        {
            // for 2.1+ read ks and cf names from directory
            File cfDirectory = parentDirectory;
            // check if this is secondary index
            String indexName = "";
            if (cfDirectory.getName().startsWith(Directories.SECONDARY_INDEX_NAME_SEPARATOR))
            {
                indexName = cfDirectory.getName();
                cfDirectory = cfDirectory.getParentFile();
            }
            if (cfDirectory.getName().equals(Directories.BACKUPS_SUBDIR))
            {
                cfDirectory = cfDirectory.getParentFile();
            }
            else if (cfDirectory.getParentFile().getName().equals(Directories.SNAPSHOT_SUBDIR))
            {
                cfDirectory = cfDirectory.getParentFile().getParentFile();
            }
            cfname = cfDirectory.getName().split("-")[0] + indexName;
            ksname = cfDirectory.getParentFile().getName();
        }
        else
        {
            cfname = tokenStack.pop();
            ksname = tokenStack.pop();
        }
        assert tokenStack.isEmpty() : "Invalid file name " + name + " in " + directory;

        return Pair.create(new Descriptor(version, parentDirectory, ksname, cfname, generation, fmt), component);
    }

    public IMetadataSerializer getMetadataSerializer()
    {
        if (version.hasNewStatsFile())
            return new MetadataSerializer();
        else
            return new LegacyMetadataSerializer();
    }

    /**
     * @return true if the current Cassandra version can read the given sstable version
     */
    public boolean isCompatible()
    {
        return version.isCompatible();
    }

    @Override
    public String toString() //不包含component
    {
        return baseFilename();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
            return true;
        if (!(o instanceof Descriptor))
            return false;
        Descriptor that = (Descriptor)o;
        return that.directory.equals(this.directory)
                       && that.generation == this.generation
                       && that.ksname.equals(this.ksname)
                       && that.cfname.equals(this.cfname)
                       && that.formatType == this.formatType;
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }
}
