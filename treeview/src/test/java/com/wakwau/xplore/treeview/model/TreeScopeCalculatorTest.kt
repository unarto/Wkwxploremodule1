package com.wakwau.xplore.treeview.model

import com.wakwau.xplore.treeview.state.TreeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TreeScopeCalculatorTest {

    private lateinit var treeState: TreeState<String>

    @Before
    fun setup() {
        treeState = TreeState()
    }

    @Test
    fun `test null or non-matching focus returns null`() {
        val root = TreeNode("Root", id = "root_id")
        treeState.setRoots(listOf(root))

        val visible = treeState.visibleNodes.value

        assertNull(TreeScopeCalculator.calculateFocusRange(visible, null))
        assertNull(TreeScopeCalculator.calculateFocusRange(visible, "non_existent"))
        assertEquals(BorderPosition.NONE, TreeScopeCalculator.getBorderPosition(0, null))
    }

    @Test
    fun `test single leaf node focus returns SINGLE`() {
        val leafNode = TreeNode("Leaf", id = "leaf_id")
        treeState.setRoots(listOf(leafNode))

        val visible = treeState.visibleNodes.value
        val range = TreeScopeCalculator.calculateFocusRange(visible, "leaf_id")

        assertEquals(0..0, range)
        assertEquals(BorderPosition.SINGLE, TreeScopeCalculator.getBorderPosition(0, range))
        assertEquals(BorderPosition.NONE, TreeScopeCalculator.getBorderPosition(1, range))
    }

    @Test
    fun `test collapsed parent node focus returns SINGLE`() {
        val root = TreeNode("Parent", id = "parent_id")
        val child = TreeNode("Child", id = "child_id")
        root.addChild(child)
        // Root is collapsed
        treeState.setRoots(listOf(root))

        val visible = treeState.visibleNodes.value
        val range = TreeScopeCalculator.calculateFocusRange(visible, "parent_id")

        assertEquals(0..0, range)
        assertEquals(BorderPosition.SINGLE, TreeScopeCalculator.getBorderPosition(0, range))
    }

    @Test
    fun `test expanded parent with children returns TOP MIDDLE BOTTOM`() {
        val root = TreeNode("Parent", id = "parent_id")
        val child1 = TreeNode("Child1", id = "c1")
        val child2 = TreeNode("Child2", id = "c2")
        val child3 = TreeNode("Child3", id = "c3")
        root.addChild(child1)
        root.addChild(child2)
        root.addChild(child3)
        root.expand()

        treeState.setRoots(listOf(root))
        val visible = treeState.visibleNodes.value

        val range = TreeScopeCalculator.calculateFocusRange(visible, "parent_id")
        assertEquals(0..3, range)

        assertEquals(BorderPosition.TOP, TreeScopeCalculator.getBorderPosition(0, range))
        assertEquals(BorderPosition.MIDDLE, TreeScopeCalculator.getBorderPosition(1, range))
        assertEquals(BorderPosition.MIDDLE, TreeScopeCalculator.getBorderPosition(2, range))
        assertEquals(BorderPosition.BOTTOM, TreeScopeCalculator.getBorderPosition(3, range))
    }

    @Test
    fun `test expanded node with nested subtree focus range covers all descendants`() {
        val root = TreeNode("Root", id = "root_id")
        val branch = TreeNode("Branch", id = "branch_id")
        val nestedChild = TreeNode("DeepChild", id = "nested_id")
        val rootChild = TreeNode("RootChild", id = "root_child_id")

        branch.addChild(nestedChild)
        root.addChild(branch)
        root.addChild(rootChild)

        root.expand()
        branch.expand()
        treeState.setRoots(listOf(root))

        val visible = treeState.visibleNodes.value
        // visible: [0: Root, 1: Branch, 2: DeepChild, 3: RootChild]
        assertEquals(4, visible.size)

        // Focus on Root: covers 0..3
        val rootRange = TreeScopeCalculator.calculateFocusRange(visible, "root_id")
        assertEquals(0..3, rootRange)
        assertEquals(BorderPosition.TOP, TreeScopeCalculator.getBorderPosition(0, rootRange))
        assertEquals(BorderPosition.MIDDLE, TreeScopeCalculator.getBorderPosition(1, rootRange))
        assertEquals(BorderPosition.MIDDLE, TreeScopeCalculator.getBorderPosition(2, rootRange))
        assertEquals(BorderPosition.BOTTOM, TreeScopeCalculator.getBorderPosition(3, rootRange))

        // Focus on Branch: covers 1..2
        val subRange = TreeScopeCalculator.calculateFocusRange(visible, "branch_id")
        assertEquals(1..2, subRange)
        assertEquals(BorderPosition.NONE, TreeScopeCalculator.getBorderPosition(0, subRange))
        assertEquals(BorderPosition.TOP, TreeScopeCalculator.getBorderPosition(1, subRange))
        assertEquals(BorderPosition.BOTTOM, TreeScopeCalculator.getBorderPosition(2, subRange))
        assertEquals(BorderPosition.NONE, TreeScopeCalculator.getBorderPosition(3, subRange))
    }

    @Test
    fun `test expanded parent with 1 child has TOP and BOTTOM without MIDDLE`() {
        val root = TreeNode("Parent", id = "parent_id")
        val child = TreeNode("OnlyChild", id = "child_id")
        root.addChild(child)
        root.expand()

        treeState.setRoots(listOf(root))
        val visible = treeState.visibleNodes.value

        val range = TreeScopeCalculator.calculateFocusRange(visible, "parent_id")
        assertEquals(0..1, range)
        assertEquals(BorderPosition.TOP, TreeScopeCalculator.getBorderPosition(0, range))
        assertEquals(BorderPosition.BOTTOM, TreeScopeCalculator.getBorderPosition(1, range))
    }
}
