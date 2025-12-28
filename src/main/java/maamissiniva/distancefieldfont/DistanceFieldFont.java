package maamissiniva.distancefieldfont;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import maamissiniva.bitmapfontdescriptor.BitmapFontDescriptor;
import maamissiniva.bitmapfontdescriptor.CharGlyph;
import maamissiniva.bitmapfontdescriptor.Glyph;

public class DistanceFieldFont {

    private static final Logger logger = LoggerFactory.getLogger(DistanceFieldFont.class);     
    
    public static int floor(double value) {
        return (int)Math.floor(value);
    }
    
    public static int ceil(double value) {
        return (int)Math.ceil(value);
    }
    
    public static double clamp(double min, double max, double value) {
        return max(min, min(max, value));
    }

    public static float sqrt(double v) {
        return (float)Math.sqrt(v);
    }
    
    public static class DistanceFieldFontConfiguration {
        public int    outputWidth;
        public int    outputHeight;
        public int    spread;
        public double flatness;
        public String fontFilename;
        public int    fontSize;
        public String outputFilename;
    }
    
    public static List<Segment> segments(PathIterator pi) {
        List<Segment> segments = new ArrayList<>();
        float[] coords = new float[6];
        double lastx = 0,
                lasty = 0,
                firstx = 0,
                firsty = 0;
        while (! pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
            case PathIterator.SEG_LINETO:
                segments.add(new Segment(lastx, lasty, coords[0], coords[1]));
                lastx = coords[0];
                lasty = coords[1];
                break;
            case PathIterator.SEG_MOVETO:
                firstx = lastx = coords[0];
                firsty = lasty = coords[1];
                break;
            case PathIterator.SEG_QUADTO:
                throw new RuntimeException("SEG QUAD TO should have been linearized");
            case PathIterator.SEG_CUBICTO:
                throw new RuntimeException("SEG CUBIC TO should have been linearized");
            case PathIterator.SEG_CLOSE:
                segments.add(new Segment(coords[0], coords[1], firstx, firsty));
                break;
            }
            pi.next();
        }
        return segments;
    }
    
    public static GeneralPath shape(PathIterator pi) {
        float[] coords = new float[6];
        GeneralPath gp = new GeneralPath();
        while (! pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
            case PathIterator.SEG_LINETO:
                gp.lineTo(coords[0], coords[1]);
                break;
            case PathIterator.SEG_MOVETO:
                gp.moveTo(coords[0], coords[1]);
                break;
            case PathIterator.SEG_QUADTO:
                throw new RuntimeException("SEG QUAD TO should have been linearized");
            case PathIterator.SEG_CUBICTO:
                throw new RuntimeException("SEG CUBIC TO should have been linearized");
            case PathIterator.SEG_CLOSE:
                gp.closePath();
                break;
            }
            pi.next();
        }
        return gp;
    }
    
    static class Segment {
        double x0,y0,x1,y1;
        public Segment(double x0, double y0, double x1, double y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }
    }

    public static Rectangle2D translate(double dx, double dy, Rectangle2D r) {
        return new Rectangle2D.Double(r.getX() + dx, r.getY() + dy, r.getWidth(), r.getHeight());
    }
    
    static Graphics2D setupImage(BufferedImage i) {
        Graphics2D g = i.createGraphics();
//        g.setColor(new Color(0, 0, 0, 0)); // maximum distance in all components.
        g.setColor(Color.red); 
        g.fillRect(0, 0, i.getWidth(), i.getHeight());
        return g;
    }
    
    static float signedDistance(float x, float y, float max, List<Segment> segments, Shape shape) {
        double minD2 = max * max;
        // compute minimal distance to segments.
        for (Segment s : segments) 
            minD2 = min(minD2, Line2D.ptSegDistSq(s.x0, s.y0, s.x1, s.y1, x, y));
        if (shape.contains(x, y))
            return sqrt(minD2);
        return - sqrt(minD2);
    }
    
    public static void generateDistanceField(DistanceFieldFontConfiguration cfg) throws Exception {
        // Font at requested size.
        logger .debug("using font file:{}, size:{}", cfg.fontFilename, cfg.fontSize);
        Font font = Font
                .createFont(Font.TRUETYPE_FONT, new File(cfg.fontFilename))
                .deriveFont(Font.PLAIN,         cfg.fontSize);        
        // Generated glyphs
        List<Glyph>  glyphs = new ArrayList<>();
        List<CharGlyph> charGlyphs = new ArrayList<>();
        // Bitmap font image that will be output.
        BufferedImage distanceFieldImage = new BufferedImage(cfg.outputWidth, cfg.outputHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D  o          = setupImage(distanceFieldImage);
        FontMetrics fm         = o.getFontMetrics(font);
        // Compute font information relative to the bottom
        int maxDescent = fm.getMaxDescent();
        int maxAscent  = fm.getMaxAscent();
        int ascent     = fm.getAscent();
        int descent    = fm.getDescent();
        int leading    = fm.getLeading();
        int lineHeight = fm.getHeight();
        logger.debug("max ascent:{}, ascent:{}, descent:{}, max descent:{}", maxAscent, ascent, descent, maxDescent);
        logger.debug("lineHeight:{}, leading:{}", lineHeight, leading);
        int botDescent   = maxDescent - descent;
        int botBaseline  = maxDescent - 0;
        int botAscent    = maxDescent + ascent;
        int botMaxAscent = maxDescent + maxAscent;
        logger.debug("bot descent:{}, base:{}, ascent:{}, maxAscent:{}", botDescent, botBaseline, botAscent, botMaxAscent);
        // Compute a glyph + spread bounding box. We use botMaxAscent as the height bound
        // take 1 pixel margin above and below. There is no optimization when "packing".
        int spread = cfg.spread;
        int pad    = cfg.spread + 1;
        int glyphHeight = botMaxAscent + pad * 2;
        // Start drawing with enough space to draw a full glyph.
        int         glx        = 1; 
        int         gly        = 1;
        FontRenderContext ctx = new FontRenderContext(null, false, true);
        // Buffer to render a single char
        char[] gc = new char[1];
        // Map of java glyph code to bitmap glyph index
        Map<Integer, Integer> javaGlyphCodeToBitmapGlyphIndex = new HashMap<>();
        float txWidth = cfg.outputWidth;
        float txHeight = cfg.outputHeight;
        for (char c = 0; c <= 255; c++) {
            gc[0] = c;
            GlyphVector gv = font.createGlyphVector(ctx, gc);
            int gcode = gv.getGlyphCode(0);
            if (javaGlyphCodeToBitmapGlyphIndex.containsKey(gcode)) {
                // charToGlyphIndex.put((int)c, javaGlyphCodeToBitmapGlyphIndex.get(gcode));
                charGlyphs.add(new CharGlyph(c, javaGlyphCodeToBitmapGlyphIndex.get(gcode)));
                continue;
            }
            Shape       gs = gv.getGlyphOutline(0);
            GlyphMetrics metrics = gv.getGlyphMetrics(0);
            Rectangle2D gb = gs.getBounds2D();
            // Logical bounds are baseline relative. 
            // The target bitmap pixels are around the glyph shape bounding box.
            // [-4.5,+4.5]
            int minx = floor(gb.getX())   - pad;
            int miny = floor(gb.getY())   - pad;
            int maxx = ceil(gb.getMaxX()) + pad;
            int maxy = ceil(gb.getMaxY()) + pad;
            // Next line of chars
            if (glx + maxx - minx >= cfg.outputWidth) {
                gly += glyphHeight;
                glx = 1;
            }
            if (gly + lineHeight >= cfg.outputHeight)
                break;
            
            javaGlyphCodeToBitmapGlyphIndex.put(gcode, glyphs.size());
            charGlyphs.add(new CharGlyph(c, glyphs.size()));

            GeneralPath     ga        = shape(gs.getPathIterator(null, cfg.flatness));
            List<Segment>   segments  = segments(gs.getPathIterator(null, cfg.flatness));
            for (int gx = minx; gx < maxx; gx++) {
                for (int gy = miny; gy < maxy; gy++) {
                    float px = gx + .5f; 
                    float py = gy + .5f;
                    float sd = signedDistance(px, py, spread, segments, ga); // [-spread,spread]
                    float cval = (sd + spread) / (cfg.spread + cfg.spread);
                    Color col = new Color(cval, cval, cval, cval);
                    int tx = glx + gx - minx;
                    int ty = gly + gy - miny;
                    distanceFieldImage.setRGB(tx, ty, col.getRGB());
                }
            }
            // Compute glyph layout information
            Glyph g = new Glyph();
            g.advance = metrics.getAdvance();
            g.texture    = 0; // only one page generated at this point
            // Glyph bounds are relative to the baseline
            g.drawX      = minx;
            // y+ is down 
            g.drawY      = botBaseline - maxy;
            g.drawWidth  = maxx - minx;
            g.drawHeight = maxy - miny;
            g.u0         = (glx) / txWidth; 
            g.v0         = (gly + maxy - miny) / txHeight;
            g.u1         = (glx + maxx - minx) / txWidth; 
            g.v1         = (gly) / txHeight; 
            g.lx         = (float)gb.getX();
            g.ly         = (float)(botBaseline - (gb.getY() + gb.getHeight()));
            g.lwidth     = (float)gb.getWidth();
            g.lheight    = (float)gb.getHeight();
            glyphs.add(g);
            glx += maxx - minx + 1;
        }
        File outputBaseName = new File(cfg.outputFilename);
        File pngFile        = new File(outputBaseName.getParentFile(), outputBaseName.getName() + ".png");
        File fntFile        = new File(outputBaseName.getParentFile(), outputBaseName.getName() + ".dff");
        logger.debug("output {} glyphs", glyphs.size());
        ImageIO.write(distanceFieldImage, "png", pngFile);
        // Write JSON font file
        BitmapFontDescriptor d = new BitmapFontDescriptor();
        d.name     = font.getName();
        d.size     = cfg.fontSize; 
        d.top      = botMaxAscent;
        d.ascent   = botAscent;
        d.baseline = botBaseline;
        d.descent  = botDescent;
        d.textures = Arrays.asList(pngFile.getName());
        d.chars    = charGlyphs;
        d.glyphs   = glyphs;
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(fntFile, d);
    }
    
    public static void generateDistanceField(File ttfFile, File outputDir) throws Exception {
        generateDistanceField(ttfFile.toString(), outputDir.toString());
    }
    
    public static void generateDistanceField(String ttfFile, String outputDir) throws Exception {
        DistanceFieldFontConfiguration cfg = new DistanceFieldFontConfiguration();
        // Output image dimension
        cfg.outputWidth  = 512;
        cfg.outputHeight = 512;
        // Spread in output image
        cfg.spread       = 4;
        // Font to process
        cfg.fontFilename = ttfFile;
        // Font size
        cfg.fontSize     = 32;
        // 
        cfg.flatness     = 0.1;
        // output base file name
        String baseFilename = new File(ttfFile).getName().replaceAll("\\s", "-").toString();
        baseFilename = baseFilename.substring(0, baseFilename.lastIndexOf('.'));
        cfg.outputFilename = new File(new File(outputDir), baseFilename).toString();
        generateDistanceField(cfg);
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("need <ttf file name> <output directory>");
            System.exit(1);
        }
        generateDistanceField(args[0], args[1]);
    }

}
