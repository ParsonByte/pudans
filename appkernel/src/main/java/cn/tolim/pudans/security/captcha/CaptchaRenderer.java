package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class CaptchaRenderer {

    private static final int WIDTH = 180;

    private static final int HEIGHT = 60;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Color[] COLORS = {
        new Color(32, 86, 150), new Color(146, 54, 54), new Color(46, 112, 62),
        new Color(110, 70, 140), new Color(160, 100, 30)
    };

    // 按配置生成文字或算术验证码图片
    public RenderedCaptcha render(ParameterSetting.Captcha config) {
        var mode = config.getCaptchaMode() == null ? "TEXT" : config.getCaptchaMode().toUpperCase(Locale.ROOT);
        var payload = "MATH".equals(mode)
                ? mathPayload()
                : new CaptchaPayload(null, textAnswer(captchaLength(config)));
        var display = payload.display() == null ? payload.answer() : payload.display();
        var image = draw(display, config);
        return new RenderedCaptcha(
                payload.answer(), "data:image/png;base64," + Base64.getEncoder().encodeToString(image));
    }

    // 限制验证码长度在可渲染范围内
    private static int captchaLength(ParameterSetting.Captcha config) {
        var length = config.getCaptchaLength() == null ? 5 : config.getCaptchaLength();
        return Math.max(3, Math.min(8, length));
    }

    // 从易混淆字符集中随机生成答案
    private static String textAnswer(int length) {
        var builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    // 生成个位数加减法题目与答案
    private static CaptchaPayload mathPayload() {
        var left = 1 + RANDOM.nextInt(9);
        var right = 1 + RANDOM.nextInt(9);
        if (RANDOM.nextBoolean()) {
            return new CaptchaPayload(left + " + " + right + " = ?", String.valueOf(left + right));
        }
        var minuend = Math.max(left, right);
        var subtrahend = Math.min(left, right);
        return new CaptchaPayload(minuend + " - " + subtrahend + " = ?", String.valueOf(minuend - subtrahend));
    }

    // 绘制底图、干扰元素与文字并编码
    private byte[] draw(String display, ParameterSetting.Captcha config) {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 246, 249));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            drawInterference(graphics, config);
            drawAnswer(graphics, display);
        } finally {
            graphics.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG 验证码编码不可用");
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("无法编码验证码图片", e);
        }
    }

    // 逐字符随机旋转着色，增加识别难度
    private void drawAnswer(Graphics2D graphics, String display) {
        var fontSize = Math.max(20, Math.min(34, 150 / Math.max(1, display.length())));
        var font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        graphics.setFont(font);
        var textWidth = graphics.getFontMetrics().stringWidth(display);
        var x = (WIDTH - textWidth) / 2;
        for (int i = 0; i < display.length(); i++) {
            var character = display.substring(i, i + 1);
            var characterWidth = graphics.getFontMetrics().stringWidth(character);
            var original = graphics.getTransform();
            graphics.rotate((RANDOM.nextDouble() - 0.5) * 0.45, x + characterWidth / 2.0, HEIGHT / 2.0);
            graphics.setColor(COLORS[RANDOM.nextInt(COLORS.length)]);
            graphics.drawString(character, x, 42);
            graphics.setTransform(original);
            x += characterWidth;
        }
    }

    // 按配置叠加噪点、曲线与干扰文字
    private void drawInterference(Graphics2D graphics, ParameterSetting.Captcha config) {
        if (Boolean.TRUE.equals(config.getCaptchaUseNoise())
                || Boolean.TRUE.equals(config.getCaptchaInterferenceDots())) {
            var points = Boolean.TRUE.equals(config.getCaptchaInterferenceDots()) ? 320 : 160;
            for (int i = 0; i < points; i++) {
                graphics.setColor(randomLightColor());
                graphics.fillOval(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 2, 2);
            }
        }
        if (Boolean.TRUE.equals(config.getCaptchaUseWaveLine())) {
            drawWave(graphics, 3, 8);
        }
        if (Boolean.TRUE.equals(config.getCaptchaUseSineLine())) {
            drawSine(graphics, 3, 6);
        }
        if (Boolean.TRUE.equals(config.getCaptchaUseHollowLine())) {
            drawHollowLine(graphics);
        }
        if (Boolean.TRUE.equals(config.getCaptchaUseInterferenceText())
                || Boolean.TRUE.equals(config.getCaptchaInterferenceText())) {
            var amount = Boolean.TRUE.equals(config.getCaptchaInterferenceText()) ? 12 : 6;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            for (int i = 0; i < amount; i++) {
                graphics.setColor(randomLightColor());
                graphics.drawString(
                        String.valueOf(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()))),
                        RANDOM.nextInt(WIDTH),
                        RANDOM.nextInt(HEIGHT));
            }
        }
    }

    // 绘制随机相位的波浪干扰线
    private void drawWave(Graphics2D graphics, int lines, int amplitude) {
        for (int line = 0; line < lines; line++) {
            var path = new Path2D.Double();
            path.moveTo(0, HEIGHT / 2.0);
            for (int x = 0; x <= WIDTH; x += 6) {
                path.lineTo(x, HEIGHT / 2.0 + Math.sin((x + line * 20) / 18.0) * amplitude);
            }
            graphics.setColor(randomLightColor());
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.draw(path);
        }
    }

    // 绘制正弦曲线干扰线
    private void drawSine(Graphics2D graphics, int lines, int amplitude) {
        for (int line = 0; line < lines; line++) {
            var path = new Path2D.Double();
            path.moveTo(0, 10 + line * 12.0);
            for (int x = 0; x <= WIDTH; x += 4) {
                path.lineTo(x, 10 + line * 12.0 + Math.sin(x / 10.0) * amplitude);
            }
            graphics.setColor(randomLightColor());
            graphics.setStroke(new BasicStroke(1.2f));
            graphics.draw(path);
        }
    }

    // 绘制双层线条形成空心干扰线
    private void drawHollowLine(Graphics2D graphics) {
        var y = 10 + RANDOM.nextInt(HEIGHT - 20);
        graphics.setColor(randomLightColor());
        graphics.setStroke(new BasicStroke(4f));
        graphics.drawLine(0, y, WIDTH, y + RANDOM.nextInt(10) - 5);
        graphics.setColor(new Color(244, 246, 249));
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawLine(0, y, WIDTH, y);
    }

    // 干扰元素使用半透明浅色避免遮挡
    private static Color randomLightColor() {
        var color = COLORS[RANDOM.nextInt(COLORS.length)];
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 110);
    }

    private record CaptchaPayload(String display, String answer) {}

    public record RenderedCaptcha(String answer, String imageDataUrl) {}
}
