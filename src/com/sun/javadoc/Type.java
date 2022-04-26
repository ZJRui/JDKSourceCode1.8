/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.javadoc;

/**
 * Represents a type.  A type can be a class or interface, an
 * invocation (like {@code List<String>}) of a generic class or interface,
 * a type variable, a wildcard type ("<code>?</code>"),
 * or a primitive data type (like <code>char</code>).
 *
 * @since 1.2
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 * @author Scott Seligman (generics)
 */
public interface Type {
    /**
     *
     * Type 是所有类型的父接口， 他有四个子接口和一个实现类
     *
     * 实现类：class  ，子接口：ParameterizedType 、GenericArrayType、TypeVariable 、WildcardType
     *
     * ParameterizedType表示的是参数化类型，例如List<String> Map<Integer,String> Service<User>这种带有泛型的类型。
     *    ParameterizedType接口中常用的方法又三个：分别是
     *       Type  getRawType() 返回参数化类型中的原始类型，列如List<String>的原始类型是List
     *       Type[] getActualTypeArguments() 获取参数化类型的类型变量或是实际类型列表。列如Map<Integer,String> 的实际泛型列表Integer和String。需要注意
     *       的是，该列表的元素类型都是Type，也就是说可能存在多层嵌套的情况。
     *       Type getOwnerType() 返回的是类型所属的类型，例如存在A<T>类，其中定义了内部类InnterA<I> 则InnerA<I>所属的类型为A<T>.如果是顶层类型则返回null.这种
     *       关系比较常见的实例是Map<K,V>接口和Map.Entry<K,V>接口，Map<K,V>接口是Map.Entry<K,V>接口的所有者。
     *
     *    TypeVariable 表示的是类型变量，他用来反映JVM编译该泛型前的信息。列如List<T>中的T就是类型变量，他在编译时需被转换为一个具体的类型后才能使用。
     *        该接口中常用的方法有三个：
     *        （1）Type getBounds() 获取类型变量的上边界，如果未明确声明上边界则默认为Objects 。例如class Test <K extends Person> 中K的上边界就是Person。
     *        （2）D getGenericDeclaration()获取声明该类型变量的原始类型，例如class Test<K extends Person> 中的原始类型是Test
     *        （3）String getName() 获取在源码中定义时的名字，上列中为K
     *
     *    GenericArrayType： 表示的是数组类型且组成元素是ParameterizedType或者TypeVariable。 例如List<String>[] 或T[] .该接口只有Type getGenericComponentType一个方法，
     *    他返回数组的组成元素。
     *
     *    WildcardType:表示的是通配符泛型， 例如 ？ extends Number 和？ super Integer.
     *     WidcardType 接口有两个方法：
     *     （1）Type[] getUpperBounds 返回泛型变量的上边界
     *     （2）Type[] getLowerBounds() 返回泛型变量的下边界
     *
     *
     */

    /**
     * Return unqualified name of type excluding any dimension information.
     * <p>
     * For example, a two dimensional array of String returns
     * "<code>String</code>".
     */
    String typeName();

    /**
     * Return qualified name of type excluding any dimension information.
     *<p>
     * For example, a two dimensional array of String
     * returns "<code>java.lang.String</code>".
     */
    String qualifiedTypeName();

    /**
     * Return the simple name of this type excluding any dimension information.
     * This is the unqualified name of the type, except that for nested types
     * only the identifier of the innermost type is included.
     * <p>
     * For example, the class {@code Outer.Inner} returns
     * "<code>Inner</code>".
     *
     * @since 1.5
     */
    String simpleTypeName();

    /**
     * Return the type's dimension information, as a string.
     * <p>
     * For example, a two dimensional array of String returns
     * "<code>[][]</code>".
     */
    String dimension();

    /**
     * Return a string representation of the type.
     * This includes any dimension information and type arguments.
     * <p>
     * For example, a two dimensional array of String may return
     * "<code>java.lang.String[][]</code>",
     * and the parameterized type {@code List<Integer>} may return
     * "{@code java.util.List<java.lang.Integer>}".
     *
     * @return a string representation of the type.
     */
    String toString();

    /**
     * Return true if this type represents a primitive type.
     *
     * @return true if this type represents a primitive type.
     * @since 1.5
     */
    boolean isPrimitive();

    /**
     * Return this type as a <code>ClassDoc</code> if it represents a class
     * or interface.  Array dimensions are ignored.
     * If this type is a <code>ParameterizedType</code>,
     * <code>TypeVariable</code>, or <code>WildcardType</code>, return
     * the <code>ClassDoc</code> of the type's erasure.  If this is an
     * <code>AnnotationTypeDoc</code>, return this as a <code>ClassDoc</code>
     * (but see {@link #asAnnotationTypeDoc()}).
     * If this is a primitive type, return null.
     *
     * @return the <code>ClassDoc</code> of this type,
     *         or null if it is a primitive type.
     */
    ClassDoc asClassDoc();

    /**
     * Return this type as a <code>ParameterizedType</code> if it represents
     * an invocation of a generic class or interface.  Array dimensions
     * are ignored.
     *
     * @return a <code>ParameterizedType</code> if the type is an
     *         invocation of a generic type, or null if it is not.
     * @since 1.5
     */
    ParameterizedType asParameterizedType();

    /**
     * Return this type as a <code>TypeVariable</code> if it represents
     * a type variable.  Array dimensions are ignored.
     *
     * @return a <code>TypeVariable</code> if the type is a type variable,
     *         or null if it is not.
     * @since 1.5
     */
    TypeVariable asTypeVariable();

    /**
     * Return this type as a <code>WildcardType</code> if it represents
     * a wildcard type.
     *
     * @return a <code>WildcardType</code> if the type is a wildcard type,
     *         or null if it is not.
     * @since 1.5
     */
    WildcardType asWildcardType();

    /**
     * Returns this type as a <code>AnnotatedType</code> if it represents
     * an annotated type.
     *
     * @return a <code>AnnotatedType</code> if the type if an annotated type,
     *         or null if it is not
     * @since 1.8
     */
    AnnotatedType asAnnotatedType();

    /**
     * Return this type as an <code>AnnotationTypeDoc</code> if it represents
     * an annotation type.  Array dimensions are ignored.
     *
     * @return an <code>AnnotationTypeDoc</code> if the type is an annotation
     *         type, or null if it is not.
     * @since 1.5
     */
    AnnotationTypeDoc asAnnotationTypeDoc();

    /**
     * If this type is an array type, return the element type of the
     * array. Otherwise, return null.
     *
     * @return a <code>Type</code> representing the element type or null.
     * @since 1.8
     */
    Type getElementType();
}
