package app.foodko.domain

import app.foodko.data.model.Food

/**
 * Snapshot of the food collection with redirect resolution.
 * A food with [Food.mergedInto] set is a redirect; chains (A→B→C) are followed to the end.
 */
class FoodIndex(foods: List<Food>) {

    private val byId: Map<String, Food> = foods.associateBy { it.id }

    /** Foods shown in lists and the picker: live, not archived, sorted by label. */
    val visible: List<Food> = foods
        .filter { it.mergedInto == null && !it.archived }
        .sortedBy { it.labelLower }

    val archived: List<Food> = foods
        .filter { it.mergedInto == null && it.archived }
        .sortedBy { it.labelLower }

    val isEmpty: Boolean get() = byId.isEmpty()

    /** Final id after following redirects. Unknown ids are returned unchanged. */
    fun resolveId(id: String): String {
        var current = id
        val seen = HashSet<String>()
        while (seen.add(current)) {
            val next = byId[current]?.mergedInto ?: return current
            current = next
        }
        return current // cycle; should never happen, don't loop forever
    }

    fun resolve(id: String): Food? = byId[resolveId(id)]

    /**
     * Foods whose label contains every word of [query], in any order, ignoring case and
     * diacritics. Empty query returns [source] unchanged. Linear scan: trivially fast for the
     * few thousand foods two people can type in.
     */
    fun search(query: String, source: List<Food> = visible): List<Food> {
        val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return source
        return source.filter { food -> words.all { it in food.labelLower } }
    }

    fun isRedirected(id: String): Boolean = resolveId(id) != id

    /** Map of redirected id → final id, restricted to [ids]. Empty when nothing needs rewriting. */
    fun redirectsAmong(ids: Collection<String>): Map<String, String> =
        ids.distinct().mapNotNull { id ->
            val final = resolveId(id)
            if (final != id) id to final else null
        }.toMap()
}
