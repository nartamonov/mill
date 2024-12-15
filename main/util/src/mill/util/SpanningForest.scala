package mill.util

import scala.collection.mutable

/**
 * Algorithm to compute the minimal spanning forest of a directed acyclic graph
 * that covers a particular subset of [[importantVertices]] (a "Steiner Forest"),
 * minimizing the maximum height of the resultant trees. When multiple solutions
 * exist with the same height, one chosen is arbitrarily. (This is much simpler
 * than the "real" algorithm which aims to minimize the sum of edge/vertex weights)
 *
 * Returns the forest as a [[Node]] structure with the top-level node containing
 * the roots of the forest
 */
private[mill] object SpanningForest {
  def spanningTreeToJsonTree(node: SpanningForest.Node, stringify: Int => String): ujson.Obj = {
    ujson.Obj.from(
      node.values.map { case (k, v) => stringify(k) -> spanningTreeToJsonTree(v, stringify) }
    )
  }
  case class Node(values: mutable.Map[Int, Node] = mutable.Map())
  def apply(
      indexGraphEdges: Array[Array[Int]],
      importantVertices: Set[Int],
      limitToImportantVertices: Boolean
  ): Node = {
    // Find all importantVertices which are "roots" with no incoming edges
    // from other importantVertices
    val destinations = importantVertices.flatMap(indexGraphEdges(_))
    val rootChangedNodeIndices = importantVertices.filter(!destinations.contains(_))

    // Prepare a mutable tree structure that we will return, pre-populated with
    // just the first level of nodes from the `rootChangedNodeIndices`, as well
    // as a `nodeMapping` to let us easily take any node index and directly look
    // up the node in the tree
    val nodeMapping = rootChangedNodeIndices.map((_, Node())).to(mutable.Map)
    val spanningForest = Node(nodeMapping.clone())

    // Do a breadth first search from the `rootChangedNodeIndices` across the
    // reverse edges of the graph to build up the spanning forest
    breadthFirst(rootChangedNodeIndices) { index =>
      // needed to add explicit type for Scala 3.5.0-RC6
      val nextIndices = indexGraphEdges(index)
        .filter(e => !limitToImportantVertices || importantVertices(e))

      // We build up the spanningForest during a normal breadth first search,
      // using the `nodeMapping` to quickly find a vertice's tree node so we
      // can add children to it. We need to duplicate the `seen.contains` logic
      // in `!nodeMapping.contains`, because `breadthFirst` does not expose it
      for (nextIndex <- nextIndices if !nodeMapping.contains(nextIndex)) {
        val node = Node()
        nodeMapping(nextIndex) = node
        nodeMapping(index).values(nextIndex) = node
      }
      nextIndices
    }
    spanningForest
  }

  def breadthFirst[T](start: IterableOnce[T])(edges: T => IterableOnce[T]): Seq[T] = {
    val seen = collection.mutable.Set.empty[T]
    val seenList = collection.mutable.Buffer.empty[T]
    val queued = collection.mutable.Queue.from(start)

    while (queued.nonEmpty) {
      val current = queued.dequeue()
      seen.add(current)
      seenList.append(current)

      for (next <- edges(current).iterator) {
        if (!seen.contains(next)) queued.enqueue(next)
      }
    }
    seenList.toSeq
  }

}