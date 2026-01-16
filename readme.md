# Distance field bitmap font generation

## Dependency

[maven central](https://central.sonatype.com/artifact/io.github.maamissiniva/maamissiniva-distance-field-font)

## Description

Uses java TTF loading to process a font and generate the distance field bitmap font.
Signed distance with a maximum of 4 is encoded in the alpha component: [-4,4] -> [0,1].

Naive implementation:

 - Use a path iterator to get a segment approximation of a glyph
 - compute each pixel minimal distance to the segments

Create a PNG bitmap file and a DFF file that is a JSON serialization of
the [bitmap font descriptor](https://github.com/maamissiniva/bitmap-font-descriptor).

Only the 256 first chars are stored at the moment and only one page is generated.


