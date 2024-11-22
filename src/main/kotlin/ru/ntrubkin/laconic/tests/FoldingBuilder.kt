package ru.ntrubkin.laconic.tests

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.annotations.NotNull
import java.util.*
import java.util.regex.Pattern
import com.intellij.psi.impl.source.tree.JavaElementType.CODE_BLOCK as JAVA_CODE_BLOCK
import org.jetbrains.kotlin.BlockExpressionElementType as KOTLIN_CODE_BLOCK

class FoldingBuilder : FoldingBuilderEx(), DumbAware {

    private val givenCommentPattern = Pattern.compile("// [Gg]iven")
    private val whenCommentPattern = Pattern.compile("// [Ww]hen")
    private val thenCommentPattern = Pattern.compile("// [Tt]hen")

    private val arrangeCommentPattern = Pattern.compile("// [Aa]rrange")
    private val actCommentPattern = Pattern.compile("// [Aa]ct")
    private val assertCommentPattern = Pattern.compile("// [Aa]ssert")

    private val regionStarters = arrayOf(
        givenCommentPattern, thenCommentPattern,
        arrangeCommentPattern, assertCommentPattern
    )
    private val regionStoppers = arrayOf(
        givenCommentPattern, whenCommentPattern, thenCommentPattern,
        arrangeCommentPattern, actCommentPattern, assertCommentPattern
    )

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors: MutableList<FoldingDescriptor> = ArrayList()

        root.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (
                    element is PsiComment
                    && isInsideCodeBlock(element)
                    && regionStarters.anyMatch(element.text)
                ) {
                    findRegionEndElement(element)
                        ?.let { endElement ->
                            FoldingDescriptor(
                                element,
                                element.startOffset,
                                endElement.endOffset,
                                null,
                                "${element.text} ..."
                            )
                        }
                        ?.let { descriptors.add(it) }
                }
            }
        })

        return descriptors.toTypedArray<FoldingDescriptor>()
    }

    private fun isInsideCodeBlock(element: PsiElement): Boolean {
        return element.parent?.elementType
            .let { it == JAVA_CODE_BLOCK || it is KOTLIN_CODE_BLOCK }
    }

    private fun Array<Pattern>.anyMatch(target: String) = any { it.matcher(target).matches() }

    private fun findRegionEndElement(startComment: PsiComment): PsiElement? {
        val passedElements: Deque<PsiElement> = LinkedList()
        passedElements.push(startComment)
        var currentElement = startComment.nextSibling

        while (currentElement != null) {
            passedElements.push(currentElement)
            if (currentElement is PsiComment) {
                if (regionStoppers.anyMatch(currentElement.text)) {
                    return findRegionEndElement(passedElements)
                }
            }
            currentElement = currentElement.nextSibling
        }

        return findRegionEndElement(passedElements)
    }

    private fun findRegionEndElement(passedElements: Deque<PsiElement>): PsiElement? {
        return passedElements
            .also { if (passedElements.isEmpty()) return null }
            .let { passedElements.pop() }
            .also { if (passedElements.isEmpty()) return null }
            .let { passedElements.pop() }
            .also { if (it !is PsiWhiteSpace) return it }
            .also { if (passedElements.isEmpty()) return null }
            .let { passedElements.pop() }
    }

    override fun getPlaceholderText(p0: ASTNode) = null

    override fun isCollapsedByDefault(p0: ASTNode) = false
}
