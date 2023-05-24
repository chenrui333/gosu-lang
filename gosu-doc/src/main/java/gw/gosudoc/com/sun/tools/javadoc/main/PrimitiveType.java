/*
 * This file is a shadowed version of the older javadoc codebase on which gosudoc is based; borrowed from jdk 9.
 */

package gw.gosudoc.com.sun.tools.javadoc.main;

import gw.gosudoc.com.sun.javadoc.AnnotationTypeDoc;

/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
@Deprecated
class PrimitiveType implements gw.gosudoc.com.sun.javadoc.Type
{

    private final String name;

    static final PrimitiveType voidType = new PrimitiveType("void");
    static final PrimitiveType booleanType = new PrimitiveType("boolean");
    static final PrimitiveType byteType = new PrimitiveType("byte");
    static final PrimitiveType charType = new PrimitiveType("char");
    static final PrimitiveType shortType = new PrimitiveType("short");
    static final PrimitiveType intType = new PrimitiveType("int");
    static final PrimitiveType longType = new PrimitiveType("long");
    static final PrimitiveType floatType = new PrimitiveType("float");
    static final PrimitiveType doubleType = new PrimitiveType("double");

    // error type, should never actually be used
    static final PrimitiveType errorType = new PrimitiveType("");

    PrimitiveType(String name) {
        this.name = name;
    }

    /**
     * Return unqualified name of type excluding any dimension information.
     * <p>
     * For example, a two dimensional array of String returns 'String'.
     */
    public String typeName() {
        return name;
    }

    public gw.gosudoc.com.sun.javadoc.Type getElementType() {
        return null;
    }

    /**
     * Return qualified name of type excluding any dimension information.
     *<p>
     * For example, a two dimensional array of String
     * returns 'java.lang.String'.
     */
    public String qualifiedTypeName() {
        return name;
    }

    /**
     * Return the simple name of this type.
     */
    public String simpleTypeName() {
        return name;
    }

    /**
     * Return the type's dimension information, as a string.
     * <p>
     * For example, a two dimensional array of String returns '[][]'.
     */
    public String dimension() {
        return "";
    }

    /**
     * Return this type as a class.  Array dimensions are ignored.
     *
     * @return a ClassDocImpl if the type is a Class.
     * Return null if it is a primitive type..
     */
    public gw.gosudoc.com.sun.javadoc.ClassDoc asClassDoc() {
        return null;
    }

    /**
     * Return null, as this is not an annotation type.
     */
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    /**
     * Return null, as this is not an instantiation.
     */
    public gw.gosudoc.com.sun.javadoc.ParameterizedType asParameterizedType() {
        return null;
    }

    /**
     * Return null, as this is not a type variable.
     */
    public gw.gosudoc.com.sun.javadoc.TypeVariable asTypeVariable() {
        return null;
    }

    /**
     * Return null, as this is not a wildcard type.
     */
    public gw.gosudoc.com.sun.javadoc.WildcardType asWildcardType() {
        return null;
    }

    /**
     * Return null, as this is not an annotated type.
     */
    public gw.gosudoc.com.sun.javadoc.AnnotatedType asAnnotatedType() {
        return null;
    }

    /**
     * Returns a string representation of the type.
     *
     * Return name of type including any dimension information.
     * <p>
     * For example, a two dimensional array of String returns
     * <code>String[][]</code>.
     *
     * @return name of type including any dimension information.
     */
    public String toString() {
        return qualifiedTypeName();
    }

    /**
     * Return true if this is a primitive type.
     */
    public boolean isPrimitive() {
        return true;
    }
}