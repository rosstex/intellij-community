package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import static com.jetbrains.python.psi.FutureFeature.UNICODE_LITERALS;

/**
 * @author Alexey.Ivanov
 */
public class PyByteLiteralInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.byte.literal");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitComment(PsiComment node) {
      checkString(node, node.getText());
    }
    
    private void checkString(PsiElement node, String value) {
      PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
      if (file == null) return;
      boolean default_bytes = false;
      if (file instanceof PyFile) {
        PyFile pyfile = (PyFile)file;
        default_bytes = (!UNICODE_LITERALS.requiredAt(pyfile.getLanguageLevel()) &&
                         !pyfile.hasImportFromFuture(UNICODE_LITERALS)
        );
      }

      final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(file.getText());
      try {
        if (charsetString != null && !Charset.forName(charsetString).equals(Charset.forName("US-ASCII")))
          default_bytes = false;
      } catch (UnsupportedCharsetException exception) {}

      boolean hasNonAscii = false;

      int length = value.length();
      char c = 0;
      for (int i = 0; i < length; ++i) {
        c = value.charAt(i);
        if (((int) c) > 255) {
          hasNonAscii = true;
          break;
        }
      }

      char first_char = Character.toLowerCase(node.getText().charAt(0));
      boolean isByte = first_char == 'b' || (default_bytes && first_char != 'u');

      if (hasNonAscii && isByte) {
        registerProblem(node, "Byte literal contains characters > 255");
      }
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      checkString(node, node.getStringValue());
    }
  }
}
