package de.ck35.metricstore.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

/**
 * A cache which holds up to n elements. The element which has not been used for the longest time
 * will be dropped when another element (n+1) is added.
 * 
 * @param <K> The cache key.
 * @param <V> The cached value.
 *
 * @author Christian Kaspari
 * @since 1.0.0
 */
public class LRUCache<K, V> implements Iterable<V> {

	private final Supplier<Integer> maxCachedEntriesSupplier;
	private final Map<K, CacheEntry<V>> cache;
	private final Comparator<Entry<K, CacheEntry<V>>> comparator;
	
	public LRUCache(int maxCachedEntries) {
		this(Suppliers.<Integer>ofInstance(maxCachedEntries));
	}
	
	public LRUCache(Supplier<Integer> maxCachedEntriesSetting) {
		this.maxCachedEntriesSupplier = maxCachedEntriesSetting;
		this.cache = new HashMap<>();
		this.comparator = new CacheEntryComparator<>();
	}
	
	public V get(K key) {
		CacheEntry<V> entry = cache.get(key);
		if(entry == null) {
			return null;
		}
		return entry.get();
	}
	
	public Iterable<V> put(K key, V value) {
		CacheEntry<V> old = cache.put(key, new CacheEntry<>(value));
		int maxCachedEntries = maxCachedEntriesSupplier.get();
		if(cache.size() > maxCachedEntries) {
		    ImmutableList.Builder<V> oldValues = ImmutableList.builder();
			List<Entry<K, CacheEntry<V>>> list = new ArrayList<>(cache.entrySet());
			Collections.sort(list, comparator);
			int index = 0;
			while(cache.size() > maxCachedEntries) {
			    oldValues.add(cache.remove(list.get(index++).getKey()).get());
			}
			return oldValues.build();
		} else {
		    return old == null ? Collections.<V>emptyList() : Collections.singleton(old.get());
		}
	}
	
	public V remove(K key) {
		CacheEntry<V> cacheEntry = cache.remove(key);
		return cacheEntry == null ? null : cacheEntry.get();
	}
	
	public int size() {
		return cache.size();
	}
	
	public void clear() {
		this.cache.clear();
	}
	
	@Override
	public Iterator<V> iterator() {
		Function<Supplier<V>, V> function = Suppliers.supplierFunction();
		return Collections2.transform(Collections.<Supplier<V>>unmodifiableCollection(cache.values()), function).iterator();
	}
	
	public static class CacheEntryComparator<K, V> implements Comparator<Entry<K, CacheEntry<V>>> {
		@Override
		public int compare(Entry<K, CacheEntry<V>> o1, Entry<K, CacheEntry<V>> o2) {
			return Long.compare(o1.getValue().getLastUse(), o2.getValue().getLastUse());
		}
	}
	
	private static class CacheEntry<V> implements Supplier<V> {
		
		private final V value;
		private long lastUse;
		
		public CacheEntry(V value) {
			this.value = Objects.requireNonNull(value);
			touch();
		}
		public final void touch() {
			this.lastUse = System.nanoTime();
		}
		@Override
		public V get() {
			touch();
			return value;
		}
		public long getLastUse() {
			return lastUse;
		}
	}
}