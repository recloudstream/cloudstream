package com.lagradost.cloudstream3.utils

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A thread-safe list backed by [SynchronizedObject].
 *
 * For iteration, wrap block in [withLock] to hold the lock for the duration:
 *   list.withLock { list.forEach { ... } }
 * 
 */
open class AtomicList<T>(
    protected var delegate: List<T> = emptyList()
) : List<T>, SynchronizedObject() {

    fun <R> withLock(block: () -> R): R = synchronized(this) { block() }

    fun set(newList: List<T>) = synchronized(this) { delegate = newList }
    fun filter(predicate: (T) -> Boolean): AtomicList<T> = synchronized(this) { AtomicList(delegate.filter(predicate)) }
    fun distinctBy(selector: (T) -> Any?): AtomicList<T> = synchronized(this) { AtomicList(delegate.distinctBy(selector)) }

    override val size: Int get() = synchronized(this) { delegate.size }
    override fun isEmpty(): Boolean = synchronized(this) { delegate.isEmpty() }
    override fun contains(element: T): Boolean = synchronized(this) { delegate.contains(element) }
    override fun containsAll(elements: Collection<T>): Boolean = synchronized(this) { delegate.containsAll(elements) }
    override fun get(index: Int): T = synchronized(this) { delegate[index] }
    override fun indexOf(element: T): Int = synchronized(this) { delegate.indexOf(element) }
    override fun lastIndexOf(element: T): Int = synchronized(this) { delegate.lastIndexOf(element) }

    // Iterators intentionally NOT synchronized, callers must use withLock { } for safe iteration.
    override fun iterator(): Iterator<T> = delegate.iterator()
    override fun listIterator(): ListIterator<T> = delegate.listIterator()
    override fun listIterator(index: Int): ListIterator<T> = delegate.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<T> = delegate.subList(fromIndex, toIndex)

    operator fun plus(element: T): AtomicList<T> = synchronized(this) { AtomicList(delegate + element) }
    operator fun plus(elements: Collection<T>): AtomicList<T> = synchronized(this) { AtomicList(delegate + elements) }
}

class AtomicMutableList<T>(
    delegate: MutableList<T> = mutableListOf()
) : AtomicList<T>(delegate), MutableList<T> {

    private val mutableDelegate get() = delegate as MutableList<T>

    override fun add(element: T): Boolean = synchronized(this) { mutableDelegate.add(element) }
    override fun add(index: Int, element: T) = synchronized(this) { mutableDelegate.add(index, element) }
    override fun addAll(elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.addAll(elements) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.addAll(index, elements) }
    override fun remove(element: T): Boolean = synchronized(this) { mutableDelegate.remove(element) }
    override fun removeAt(index: Int): T = synchronized(this) { mutableDelegate.removeAt(index) }
    override fun removeAll(elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.removeAll(elements) }
    override fun retainAll(elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.retainAll(elements) }
    override fun set(index: Int, element: T): T = synchronized(this) { mutableDelegate.set(index, element) }
    override fun clear() = synchronized(this) { mutableDelegate.clear() }

    // Iterators intentionally NOT synchronized, callers must use withLock { } for safe iteration.
    override fun iterator(): MutableIterator<T> = mutableDelegate.iterator()
    override fun listIterator(): MutableListIterator<T> = mutableDelegate.listIterator()
    override fun listIterator(index: Int): MutableListIterator<T> = mutableDelegate.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = mutableDelegate.subList(fromIndex, toIndex)
}
