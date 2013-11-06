/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.path.Normalization.CASE_FOLD_ASCII;
import static com.google.jimfs.path.Normalization.NFC;
import static com.google.jimfs.path.Normalization.NFD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.StandardAttributeProviders;
import com.google.jimfs.path.Normalization;
import com.google.jimfs.path.PathType;

import java.nio.file.InvalidPathException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Immutable configuration for an in-memory file system instance.
 *
 * @author Colin Decker
 */
public final class Configuration {

  /**
   * <p>Returns the default configuration for a UNIX-like file system. A file system created with
   * this configuration:
   *
   * <ul>
   *   <li>uses "/" as the path name separator (see {@link PathType#unix()} for more information on
   *   the path format)</li>
   *   <li>has root "/" and working directory "/work"</li>
   *   <li>performs case-sensitive file lookup</li>
   *   <li>supports only the {@code basic} file attribute view, in the interest of avoiding
   *   overhead for unneeded attributes</li>
   * </ul>
   *
   * <p>To create a modified version of this configuration, such as to include the full set of UNIX
   * file attribute views, {@linkplain #toBuilder() create a builder}.
   *
   * <p>Example:
   *
   * <pre>
   *   Configuration config = Configuration.unix().toBuilder()
   *       .setAttributeViews("basic", "owner", "posix", "unix")
   *       .setWorkingDirectory("/home/user")
   *       .build();  </pre>
   */
  public static Configuration unix() {
    return UnixHolder.UNIX;
  }

  private static final class UnixHolder {
    private static final Configuration UNIX = Configuration.builder(PathType.unix())
        .setRoots("/")
        .setWorkingDirectory("/work")
        .setAttributeViews("basic")
        .build();
  }

  /**
   * <p>Returns the default configuration for a Mac OS X-like file system.
   *
   * <p>The primary differences between this configuration and the default {@link #unix()}
   * configuration are that this configuration does Unicode normalization on the display and
   * canonical forms of filenames and does case insensitive file lookup.
   *
   * <p>A file system created with this configuration:
   *
   * <ul>
   *   <li>uses "/" as the path name separator (see {@link PathType#unix()} for more information
   *   on the path format)</li>
   *   <li>has root "/" and working directory "/work"</li>
   *   <li>does Unicode normalization on paths, both for lookup and for {@code Path} objects</li>
   *   <li>does case-insensitive (for ASCII characters only) lookup</li>
   *   <li>supports only the {@code basic} file attribute view, to avoid overhead for unneeded
   *   attributes</li>
   * </ul>
   *
   * <p>To create a modified version of this configuration, such as to include the full set of UNIX
   * file attribute views or to use full Unicode case insensitivity,
   * {@linkplain #toBuilder() create a builder}.
   *
   * <p>Example:
   *
   * <pre>
   *   Configuration config = Configuration.osx().toBuilder()
   *       .setAttributeViews("basic", "owner", "posix", "unix")
   *       .setNameCanonicalNormalization(NFD, CASE_FOLD_UNICODE)
   *       .setWorkingDirectory("/Users/user")
   *       .build();  </pre>
   */
  public static Configuration osx() {
    return OsxHolder.OSX;
  }

  private static final class OsxHolder {
    private static final Configuration OSX = unix().toBuilder()
        .setNameDisplayNormalization(NFC) // matches JDK 1.7u40+ behavior
        .setNameCanonicalNormalization(NFD, CASE_FOLD_ASCII) // NFD is default in HFS+
        .build();
  }

  /**
   * <p>Returns the default configuration for a Windows-like file system. A file system created
   * with this configuration:
   *
   * <ul>
   *   <li>uses "\" as the path name separator and recognizes "/" as a separator when parsing
   *   paths (see {@link PathType#windows()} for more information on path format)</li>
   *   <li>has root "C:\" and working directory "C:\work"</li>
   *   <li>performs case-insensitive (for ASCII characters only) file lookup</li>
   *   <li>creates {@code Path} objects that use case-insensitive (for ASCII characters only)
   *   equality</li>
   *   <li>supports only the {@code basic} file attribute view, to avoid overhead for unneeded
   *   attributes</li>
   * </ul>
   *
   * <p>To create a modified version of this configuration, such as to include the full set of
   * Windows file attribute views or to use full Unicode case insensitivity,
   * {@linkplain #toBuilder() create a builder}.
   *
   * <p>Example:
   *
   * <pre>
   *   Configuration config = Configuration.windows().toBuilder()
   *       .setAttributeViews("basic", "owner", "dos", "acl", "user")
   *       .setNameCanonicalNormalization(CASE_FOLD_UNICODE)
   *       .setWorkingDirectory("C:\\Users\dir")
   *       .build();  </pre>
   */
  public static Configuration windows() {
    return WindowsHolder.WINDOWS;
  }

  private static final class WindowsHolder {
    private static final Configuration WINDOWS = Configuration.builder(PathType.windows())
        .setRoots("C:\\")
        .setWorkingDirectory("C:\\work")
        .setNameCanonicalNormalization(CASE_FOLD_ASCII)
        .setPathEqualityUsesCanonicalForm(true) // matches real behavior of WindowsPath
        .setAttributeViews("basic")
        .build();
  }

  /**
   * Creates a new mutable {@link Configuration} builder using the given path type.
   */
  public static Builder builder(PathType pathType) {
    return new Builder(pathType);
  }

  // Path configuration
  private final PathType pathType;
  private final ImmutableSet<Normalization> nameDisplayNormalization;
  private final ImmutableSet<Normalization> nameCanonicalNormalization;
  private final boolean pathEqualityUsesCanonicalForm;

  // Attribute configuration
  private final ImmutableSet<String> attributeViews;
  private final ImmutableSet<AttributeProvider> attributeProviders;
  private final ImmutableMap<String, Object> defaultAttributeValues;

  // Other
  private final ImmutableSet<String> roots;
  private final String workingDirectory;

  /**
   * Creates an immutable configuration object from the given builder.
   */
  private Configuration(Builder builder) {
    this.pathType = builder.pathType;
    this.nameDisplayNormalization = builder.nameDisplayNormalization;
    this.nameCanonicalNormalization = builder.nameCanonicalNormalization;
    this.pathEqualityUsesCanonicalForm = builder.pathEqualityUsesCanonicalForm;
    this.attributeViews = builder.attributeViews;
    this.attributeProviders = builder.attributeProviders == null
        ? ImmutableSet.<AttributeProvider>of()
        : ImmutableSet.copyOf(builder.attributeProviders);
    this.defaultAttributeValues = builder.defaultAttributeValues == null
        ? ImmutableMap.<String, Object>of()
        : ImmutableMap.copyOf(builder.defaultAttributeValues);
    this.roots = builder.roots;
    this.workingDirectory = builder.workingDirectory;
  }

  /**
   * Returns the roots for the file system.
   */
  public ImmutableList<String> roots() {
    return ImmutableList.copyOf(roots);
  }

  /**
   * Returns the working directory for the file system.
   */
  public String workingDirectory() {
    return workingDirectory;
  }

  /**
   * Returns the path type for the file system.
   */
  public PathType pathType() {
    return pathType;
  }

  /**
   * Returns the normalizations that will be applied to filenames for the string form of
   * {@code Path} objects in the file system.
   */
  public ImmutableSet<Normalization> nameDisplayNormalization() {
    return nameDisplayNormalization;
  }

  /**
   * Returns the normalizations that will be applied to the canonical form of filenames in the file
   * system.
   */
  public ImmutableSet<Normalization> nameCanonicalNormalization() {
    return nameCanonicalNormalization;
  }

  /**
   * Returns true if {@code Path} objects in the file system use the canonical form of filenames
   * for determining equality of two paths; false if they use the display form.
   */
  public boolean pathEqualityUsesCanonicalForm() {
    return pathEqualityUsesCanonicalForm;
  }

  /**
   * Returns the set of file attribute views the file system supports.
   */
  public ImmutableSet<String> attributeViews() {
    return attributeViews;
  }

  /**
   * Returns the set of custom attribute providers the file system uses to implement custom file
   * attribute views.
   */
  public ImmutableSet<AttributeProvider> attributeProviders() {
    return attributeProviders;
  }

  /**
   * Returns the set of default file attribute values for the file system. The values in the
   * returned map override the file system defaults.
   */
  public ImmutableMap<String, Object> defaultAttributeValues() {
    return defaultAttributeValues;
  }

  /**
   * Returns a new mutable builder that initially contains the same settings as this configuration.
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /**
   * Mutable builder for {@link Configuration} objects.
   */
  public static final class Builder {

    // Path configuration
    private final PathType pathType;
    private ImmutableSet<Normalization> nameDisplayNormalization = ImmutableSet.of();
    private ImmutableSet<Normalization> nameCanonicalNormalization = ImmutableSet.of();
    private boolean pathEqualityUsesCanonicalForm;

    // Attribute configuration
    private ImmutableSet<String> attributeViews = ImmutableSet.of();
    private Set<AttributeProvider> attributeProviders = null;
    private Map<String, Object> defaultAttributeValues;

    // Other
    private ImmutableSet<String> roots = ImmutableSet.of();
    private String workingDirectory;

    private Builder(PathType pathType) {
      this.pathType = checkNotNull(pathType);
    }

    private Builder(Configuration configuration) {
      this.pathType = configuration.pathType;
      this.nameDisplayNormalization = configuration.nameDisplayNormalization;
      this.nameCanonicalNormalization = configuration.nameCanonicalNormalization;
      this.pathEqualityUsesCanonicalForm = configuration.pathEqualityUsesCanonicalForm;
      this.attributeViews = configuration.attributeViews;
      this.attributeProviders = configuration.attributeProviders.isEmpty()
          ? null
          : new HashSet<>(configuration.attributeProviders);
      this.defaultAttributeValues = configuration.defaultAttributeValues.isEmpty()
          ? null
          : new HashMap<>(configuration.defaultAttributeValues);
      this.roots = configuration.roots;
      this.workingDirectory = configuration.workingDirectory;
    }

    /**
     * Sets the normalizations that will be applied to the display form of filenames. The display
     * form is used in the {@code toString()} of {@code Path} objects.
     */
    public Builder setNameDisplayNormalization(Normalization first, Normalization... more) {
      this.nameDisplayNormalization = checkNormalizations(Lists.asList(first, more));
      return this;
    }

    /**
     * Returns the normalizations that will be applied to the canonical form of filenames in the
     * file system. The canonical form is used to determine the equality of two filenames when
     * performing a file lookup.
     */
    public Builder setNameCanonicalNormalization(Normalization first, Normalization... more) {
      this.nameCanonicalNormalization = checkNormalizations(Lists.asList(first, more));
      return this;
    }

    private ImmutableSet<Normalization> checkNormalizations(List<Normalization> normalizations) {
      Normalization none = null;
      Normalization normalization = null;
      Normalization caseFold = null;
      for (Normalization n : normalizations) {
        checkNotNull(n);
        checkNormalizationNotSet(n, none);

        switch (n) {
          case NONE:
            none = n;
            break;
          case NFC:
          case NFD:
            checkNormalizationNotSet(n, normalization);
            normalization = n;
            break;
          case CASE_FOLD_UNICODE:
          case CASE_FOLD_ASCII:
            checkNormalizationNotSet(n, caseFold);
            caseFold = n;
            break;
          default:
            throw new AssertionError(); // there are no other cases
        }
      }

      if (none != null) {
        return ImmutableSet.of();
      }
      return Sets.immutableEnumSet(normalizations);
    }

    private static void checkNormalizationNotSet(Normalization n, @Nullable Normalization set) {
      if (set != null) {
        throw new IllegalArgumentException("can't set normalization " + n
            + ": normalization " + set + " already set");
      }
    }

    /**
     * Sets whether {@code Path} objects in the file system use the canonical form (true) or the
     * display form (false) of filenames for determining equality of two paths.
     */
    public Builder setPathEqualityUsesCanonicalForm(boolean useCanonicalForm) {
      this.pathEqualityUsesCanonicalForm = useCanonicalForm;
      return this;
    }

    /**
     * Sets the attribute views the file system should support. By default, the views that may be
     * specified are those listed by {@link StandardAttributeProviders}. If any other views should
     * be supported, attribute providers for those views must be
     * {@linkplain #addAttributeProvider(AttributeProvider) added}.
     */
    public Builder setAttributeViews(String first, String... more) {
      this.attributeViews = ImmutableSet.copyOf(Lists.asList(first, more));
      return this;
    }

    /**
     * Adds an attribute provider for a custom view for the file system to support.
     */
    public Builder addAttributeProvider(AttributeProvider provider) {
      checkNotNull(provider);
      if (attributeProviders == null) {
        attributeProviders = new HashSet<>();
      }
      attributeProviders.add(provider);
      return this;
    }

    /**
     * Sets the default value to use for the given file attribute when creating new files. The
     * attribute must be in the form "view:attribute". The value must be of a type that the
     * provider for the view accepts.
     *
     * <p>For the included attribute views, default values can be set for the following attributes:
     *
     * <table>
     *   <tr>
     *     <th>Attribute</th>
     *     <th>Legal Types</th>
     *   </tr>
     *   <tr>
     *     <td>{@code "owner:owner"}</td>
     *     <td>{@code String} (user name), {@code UserPrincipal}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "posix:group"}</td>
     *     <td>{@code String} (group name), {@code GroupPrincipal}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "posix:permissions"}</td>
     *     <td>{@code String} (format "rwxrw-r--"), {@code Set<PosixFilePermission>}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "dos:readonly"}</td>
     *     <td>{@code Boolean}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "dos:hidden"}</td>
     *     <td>{@code Boolean}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "dos:archive"}</td>
     *     <td>{@code Boolean}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "dos:system"}</td>
     *     <td>{@code Boolean}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code "acl:acl"}</td>
     *     <td>{@code List<AclEntry>}</td>
     *   </tr>
     * </table>
     */
    public Builder setDefaultAttributeValue(String attribute, Object value) {
      checkArgument(ATTRIBUTE_PATTERN.matcher(attribute).matches(),
          "attribute (%s) must be of the form \"view:attribute\"", attribute);
      checkNotNull(value);

      if (defaultAttributeValues == null) {
        defaultAttributeValues = new HashMap<>();
      }

      defaultAttributeValues.put(attribute, value);
      return this;
    }

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("[^:]+:[^:]+");

    /**
     * Sets the roots for the file system.
     *
     * @throws InvalidPathException if any of the given roots is not a valid path for this
     *     builder's path type
     * @throws IllegalArgumentException if any of the given roots is a valid path for this
     *     builder's path type but is not a root path with no name elements
     */
    public Builder setRoots(String first, String... more) {
      List<String> roots = Lists.asList(first, more);
      for (String root : roots) {
        PathType.ParseResult parseResult = pathType.parsePath(root);
        if (parseResult.root() == null || !Iterables.isEmpty(parseResult.names())) {
          throw new IllegalArgumentException("invalid root: " + root);
        }
      }
      this.roots = ImmutableSet.copyOf(roots);
      return this;
    }

    /**
     * Sets the path to the working directory for the file system. The working directory must be
     * an absolute path starting with one of the configured roots.
     *
     * @throws InvalidPathException if the given path is not valid for this builder's path type
     * @throws IllegalArgumentException if the given path is valid for this builder's path type but
     *     is not an absolute path
     */
    public Builder setWorkingDirectory(String workingDirectory) {
      PathType.ParseResult parseResult = pathType.parsePath(workingDirectory);
      if (!parseResult.isAbsolute()) {
        throw new IllegalArgumentException(
            "working directory must be an absolute path: " + workingDirectory);
      }
      this.workingDirectory = checkNotNull(workingDirectory);
      return this;
    }

    /**
     * Creates a new immutable configuration object from this builder.
     */
    public Configuration build() {
      return new Configuration(this);
    }
  }
}
