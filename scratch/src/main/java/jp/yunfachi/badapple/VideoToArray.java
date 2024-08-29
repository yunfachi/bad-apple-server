package jp.yunfachi.badapple;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoToArray {
    public static List<List<List<String>>> processFrames(int WIDTH, int HEIGHT, String pathTemplate, int frameCount) throws IOException {
        List<List<List<String>>> frames = new ArrayList<>();

        for (int k = 1; k <= frameCount; k++) {
            System.out.println(String.format(pathTemplate, k));
            BufferedImage image = ImageIO.read(new File(String.format(pathTemplate, k)));
            BufferedImage resizedImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
            resizedImage.getGraphics().drawImage(image, 0, 0, WIDTH, HEIGHT, null);

            List<List<String>> expandedFrame = new ArrayList<>();
            for (int i = 0; i < HEIGHT; i++) {
                List<String> row = new ArrayList<>();
                for (int j = 0; j < WIDTH; j++) {
                    int topLeft = resizedImage.getRGB(j, i) & 0xFF;

                    int topRight = j + 1 < WIDTH ? resizedImage.getRGB(j + 1, i) & 0xFF : topLeft;
                    int bottomLeft = i + 1 < HEIGHT ? resizedImage.getRGB(j, i + 1) & 0xFF : topLeft;
                    int bottomRight = (i + 1 < HEIGHT && j + 1 < WIDTH) ? resizedImage.getRGB(j + 1, i + 1) & 0xFF : topLeft;

                    String element = String.format("%d%d%d%d", topLeft > 127 ? 1 : 0, topRight > 127 ? 1 : 0, bottomLeft > 127 ? 1 : 0, bottomRight > 127 ? 1 : 0);
                    System.out.println(element + "-" + topLeft + "-" + topRight + "-" + bottomLeft + "-" + bottomRight);
                    row.add(element);
                }
                expandedFrame.add(row);
            }
            frames.add(expandedFrame);
        }

        return frames;
    }
}