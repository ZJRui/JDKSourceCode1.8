/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * Represents an invocation of a generic class or interface.  For example,
 * given the generic interface {@code List<E>}, possible invocations
 * include:
 * <pre>
 *      {@code List<String>}
 *      {@code List<T extends Number>}
 *      {@code List<?>}
 * </pre>
 * A generic inner class {@code Outer<T>.Inner<S>} might be invoked as:
 * <pre>
 *      {@code Outer<Number>.Inner<String>}
 * </pre>
 *
 * @author Scott Seligman
 * @since 1.5
 */
public interface ParameterizedType extends Type {
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
     * Return the generic class or interface that declared this type.
     *
     * @return the generic class or interface that declared this type.
     */
    ClassDoc asClassDoc();

    /**
     * Return the actual type arguments of this type.
     * For a generic type that is nested within some other generic type
     * (such as {@code Outer<T>.Inner<S>}),
     * only the type arguments of the innermost type are included.
     *
     * @return the actual type arguments of this type.
     */
    Type[] typeArguments();

    /**
     * Return the class type that is a direct supertype of this one.
     * This is the superclass of this type's declaring class,
     * with type arguments substituted in.
     * Return null if this is an interface type.
     *
     * <p> For example, if this parameterized type is
     * {@code java.util.ArrayList<String>}, the result will be
     * {@code java.util.AbstractList<String>}.
     *
     * @return the class type that is a direct supertype of this one.
     */
    Type superclassType();

    /**
     * Return the interface types directly implemented by or extended by this
     * parameterized type.
     * These are the interfaces directly implemented or extended
     * by this type's declaring class or interface,
     * with type arguments substituted in.
     * Return an empty array if there are no interfaces.
     *
     * <p> For example, the interface extended by
     * {@code java.util.Set<String>} is {@code java.util.Collection<String>}.
     *
     * @return the interface types directly implemented by or extended by this
     * parameterized type.
     */
    Type[] interfaceTypes();

    /**
     * Return the type that contains this type as a member.
     * Return null is this is a top-level type.
     *
     * <p> For example, the containing type of
     * {@code AnInterface.Nested<Number>} is the <code>ClassDoc</code>
     * representing {@code AnInterface}, and the containing type of
     * {@code Outer<String>.Inner<Number>} is the
     * <code>ParameterizedType</code> representing {@code Outer<String>}.
     *
     * @return the type that contains this type as a member.
     */
    Type containingType();
}
