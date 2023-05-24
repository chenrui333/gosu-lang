/*
 * This file is a shadowed version of the older javadoc codebase on which gosudoc is based; borrowed from jdk 9.
 */

package gw.gosudoc.com.sun.javadoc;


/**
 * Represents an annotation.
 * An annotation associates a value with each element of an annotation type.
 *
 * @author Scott Seligman
 * @since 1.5
 *
 * @deprecated
 *   The declarations in this package have been superseded by those
 *   in the package {@code jdk.javadoc.doclet}.
 *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
 */
@Deprecated
public interface AnnotationDesc {

    /**
     * Returns the annotation type of this annotation.
     *
     * @return the annotation type of this annotation.
     */
    AnnotationTypeDoc annotationType();

    /**
     * Returns this annotation's elements and their values.
     * Only those explicitly present in the annotation are
     * included, not those assuming their default values.
     * Returns an empty array if there are none.
     *
     * @return this annotation's elements and their values.
     */
    ElementValuePair[] elementValues();

    /**
     * Check for the synthesized bit on the annotation.
     *
     * @return true if the annotation is synthesized.
     */
    boolean isSynthesized();

    /**
     * Represents an association between an annotation type element
     * and one of its values.
     *
     * @author Scott Seligman
     * @since 1.5
     *
     * @deprecated
     *   The declarations in this package have been superseded by those
     *   in the package {@code jdk.javadoc.doclet}.
     *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
     */
    @Deprecated
    public interface ElementValuePair {

        /**
         * Returns the annotation type element.
         *
         * @return the annotation type element.
         */
        AnnotationTypeElementDoc element();

        /**
         * Returns the value associated with the annotation type element.
         *
         * @return the value associated with the annotation type element.
         */
        AnnotationValue value();
    }
}