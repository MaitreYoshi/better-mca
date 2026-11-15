import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Usage : java -cp asm.jar;asm-commons.jar;asm-tree.jar RelocateMca.java in.jar out.jar fabric|forge
 *  Renomme net.mca.* en <prefix>.net.mca.* (classes, chaînes, mixins, refmaps) pour le jar MCA « universal ». */
public class RelocateMca {
    static String prefix;
    static Pattern P;
    static String[] pkgs;

    static String fixStr(String s) {
        Matcher m = P.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String sep = m.group(2).contains("/") ? "/" : ".";
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + prefix + sep + m.group(2)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static class AV extends AnnotationVisitor {
        AV(AnnotationVisitor v) { super(Opcodes.ASM9, v); }
        @Override public void visit(String n, Object v) { super.visit(n, v instanceof String ? fixStr((String) v) : v); }
        @Override public AnnotationVisitor visitAnnotation(String n, String d) { return wrap(super.visitAnnotation(n, d)); }
        @Override public AnnotationVisitor visitArray(String n) { return wrap(super.visitArray(n)); }
    }
    static AnnotationVisitor wrap(AnnotationVisitor v) { return v == null ? null : new AV(v); }

    static class CV extends ClassVisitor {
        CV(ClassVisitor v) { super(Opcodes.ASM9, v); }
        @Override public AnnotationVisitor visitAnnotation(String d, boolean vis) { return wrap(super.visitAnnotation(d, vis)); }
        @Override public FieldVisitor visitField(int a, String n, String d, String sg, Object val) {
            FieldVisitor f = super.visitField(a, n, d, sg, val);
            return f == null ? null : new FieldVisitor(Opcodes.ASM9, f) {
                @Override public AnnotationVisitor visitAnnotation(String d, boolean vis) { return wrap(super.visitAnnotation(d, vis)); }
            };
        }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String sg, String[] ex) {
            MethodVisitor m = super.visitMethod(a, n, d, sg, ex);
            return m == null ? null : new MethodVisitor(Opcodes.ASM9, m) {
                @Override public AnnotationVisitor visitAnnotation(String d, boolean vis) { return wrap(super.visitAnnotation(d, vis)); }
                @Override public AnnotationVisitor visitParameterAnnotation(int p, String d, boolean vis) { return wrap(super.visitParameterAnnotation(p, d, vis)); }
            };
        }
    }

    public static void main(String[] a) throws Exception {
        prefix = a[2];
        pkgs = a.length > 3 ? a[3].split(",") : new String[] {"net.mca"};
        StringBuilder alt = new StringBuilder();
        for (String k : pkgs) alt.append(alt.length() > 0 ? "|" : "").append(k.replace(".", "[/.]"));
        P = Pattern.compile("(?<![A-Za-z0-9_$/.])(L?)(" + alt + ")(?![A-Za-z0-9_])");
        Remapper r = new Remapper() {
            @Override public String map(String n) {
                for (String k : pkgs) if (n.startsWith(k.replace('.', '/') + "/")) return prefix + "/" + n;
                return n;
            }
            @Override public Object mapValue(Object v) { return v instanceof String ? fixStr((String) v) : super.mapValue(v); }
        };
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(a[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] d = in.readAllBytes();
                String n = e.getName();
                if (n.endsWith(".class")) {
                    ClassReader cr = new ClassReader(d);
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(new ClassRemapper(new CV(cw), r), 0);
                    d = cw.toByteArray();
                    n = r.map(cr.getClassName()) + ".class";
                } else if (n.endsWith(".json") && !n.startsWith("assets/") && !n.startsWith("data/")) {
                    d = fixStr(new String(d, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry o = new ZipEntry(n);
                out.putNextEntry(o);
                out.write(d);
                out.closeEntry();
            }
        }
    }
}
