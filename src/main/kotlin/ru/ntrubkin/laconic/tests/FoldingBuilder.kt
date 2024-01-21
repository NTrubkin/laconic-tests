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
import com.intellij.psi.impl.source.tree.JavaElementType.CODE_BLOCK
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.KtNodeTypes.BLOCK
import java.util.Deque
import java.util.LinkedList
import java.util.regex.Pattern

class FoldingBuilder : FoldingBuilderEx(), DumbAware {

    private val givenCommentPattern = Pattern.compile("// [Gg]iven")
    private val whenCommentPattern = Pattern.compile("// [Ww]hen")
    private val thenCommentPattern = Pattern.compile("// [Tt]hen")
    private val blockStarters = arrayOf(givenCommentPattern, thenCommentPattern)
    private val blockStoppers = arrayOf(givenCommentPattern, whenCommentPattern, thenCommentPattern)

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors: MutableList<FoldingDescriptor> = ArrayList()

        root.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (
                    element is PsiComment
                    && isInsideMethod(element)
                    && blockStarters.anyMatch(element.text)
                ) {
                    findBlockEndElement(element)
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

    private fun isInsideMethod(element: PsiElement) = element.parent?.elementType
        .let { it == CODE_BLOCK || it == BLOCK }

    private fun Array<Pattern>.anyMatch(target: String) = any { it.matcher(target).matches() }

    private fun findBlockEndElement(startComment: PsiComment): PsiElement? {
        val passedElements: Deque<PsiElement> = LinkedList()
        passedElements.push(startComment)
        var currentElement = startComment.nextSibling

        while (currentElement != null) {
            passedElements.push(currentElement)
            if (currentElement is PsiComment) {
                if (blockStoppers.anyMatch(currentElement.text)) {
                    return findBlockEndElement(passedElements)
                }
            }
            currentElement = currentElement.nextSibling
        }

        return findBlockEndElement(passedElements)
    }

    private fun findBlockEndElement(passedElements: Deque<PsiElement>): PsiElement? {
        return passedElements.pop()
            .also { if (passedElements.isEmpty()) return null }
            .let { passedElements.pop() }
            .also { if (it is PsiWhiteSpace) return passedElements.pop() }
    }

    override fun getPlaceholderText(p0: ASTNode) = null

    override fun isCollapsedByDefault(p0: ASTNode) = false
}
