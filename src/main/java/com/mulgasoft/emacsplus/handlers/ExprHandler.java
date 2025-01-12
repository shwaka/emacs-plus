package com.mulgasoft.emacsplus.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.util.TextRange;
import com.mulgasoft.emacsplus.util.EditorUtil;

public abstract class ExprHandler extends EmacsPlusCaretHandler {
  static final boolean isVisual = true;

  protected static boolean isVisual() {
    return isVisual;
  }

  protected static void moveToWord(Editor editor, Caret caret, DataContext dataContext, int dir) {
    EditorUtil.checkMarkSelection(editor, caret);
    TextRange range = getWordRange(editor, caret, false, true, dir);
    int
        newpos =
        dir > 0 ? range.getEndOffset() : (range.getStartOffset() != caret.getOffset() ? range.getStartOffset() : 0);
    VisualPosition pos = editor.offsetToVisualPosition(newpos);
    if (isVisual()) {
      FoldRegion currentFoldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(range.getEndOffset());
      if (currentFoldRegion != null) {
        newpos = dir > 0 ? currentFoldRegion.getEndOffset() : currentFoldRegion.getStartOffset();
        pos = editor.offsetToVisualPosition(newpos);
      }

      caret.moveToVisualPosition(pos);
    } else {
      caret.moveToOffset(newpos);
    }

    EditorModificationUtil.scrollToCaret(editor);
  }

  protected static void setSelection(Editor editor, Caret caret, TextRange selection) {
    int coffset = caret.getOffset();
    int offset = coffset <= selection.getStartOffset() ? selection.getEndOffset() : selection.getStartOffset();
    setSelection(editor, caret, offset);
  }

  protected static void setSelection(Editor editor, Caret caret, int offset) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (editor.isColumnMode() && !editor.getCaretModel().supportsMultipleCarets()) {
      selectionModel.setBlockSelection(editor.offsetToLogicalPosition(offset), caret.getLogicalPosition());
    } else {
      selectionModel.setSelection(offset, caret.getVisualPosition(), caret.getOffset());
    }

  }

  public static TextRange getWordRange(Editor editor, Caret caret, boolean isLine, boolean isWord, int dir) {
    Document doc = editor.getDocument();
    int offset = caret == null ? editor.getCaretModel().getOffset() : caret.getOffset();
    CharSequence chars = editor.getDocument().getCharsSequence();
    int maxOffset = isLine ? doc.getLineEndOffset(doc.getLineNumber(offset)) : chars.length();
    offset = Math.min(offset, maxOffset);
    int newOffset = offset + dir;
    dir = dir == 0 ? -1 : dir;
    int
        startOffset =
        !isWord || dir >= 0 && (offset >= maxOffset || isIdentifierStart(chars.charAt(offset)))
        ? offset
        : -1;
    if (newOffset == maxOffset && startOffset < 0) {
      startOffset = offset;
    }

    for (; newOffset < maxOffset && newOffset > 0; newOffset += dir) {
      if (startOffset < 0) {
        char c = chars.charAt(newOffset);
        if (isIdentifierStart(c)) {
          startOffset = newOffset + (dir < 0 ? 1 : 0);
          if (dir < 0 && isWordOrLexemeStart(chars, newOffset)) {
            break;
          }
        }
      } else if (dir < 0) {
        if (isWordOrLexemeStart(chars, newOffset)) {
          break;
        }
      } else if (isWordOrLexemeEnd(chars, newOffset)) {
        break;
      }
    }

    if (dir < 0) {
      int tmp = newOffset;
      newOffset = startOffset;
      startOffset = tmp;
    }

    return newOffset <= maxOffset && newOffset >= 0 ? new TextRange(startOffset < 0 ? newOffset : startOffset,
                                                                    newOffset) : new TextRange(offset, offset);
  }

  private final static String nonWordCharacters = "\\s!-/:-@\\[-`{-~";
  // Compared with \w, wordRegex
  // - contains all non-ASCII characters
  // - does not contain _ (underscore)
  private final static String wordRegex = String.format("[^%s]", nonWordCharacters);
  private final static String nonWordRegex = String.format("[%s]", nonWordCharacters);

  private static boolean isIdentifierStart(char c) {
    // Previously this was implemented by Character.isJavaIdentifierStart(c),
    // but it does not treat digits as identifier start
    return String.valueOf(c).matches(wordRegex);
  }
  private static boolean isWordOrLexemeStart(CharSequence chars, int offset) {
    // Previously this was implemented by EditorActionUtil.isWordOrLexemeStart,
    // but it treats foo_bar as a SINGLE word (considering _ as a part of a word)
    return chars.subSequence(offset - 1, offset + 1).toString().matches(nonWordRegex + wordRegex);
  }
  private static boolean isWordOrLexemeEnd(CharSequence chars, int offset) {
    return chars.subSequence(offset - 1, offset + 1).toString().matches(wordRegex + nonWordRegex);
  }
}
