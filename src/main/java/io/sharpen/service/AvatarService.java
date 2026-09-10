package io.sharpen.service;

import io.sharpen.domain.Person;
import io.sharpen.domain.PersonAvatar;
import io.sharpen.repo.PersonAvatarRepository;
import io.sharpen.repo.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Profile pictures. Whatever is uploaded (JPEG, PNG, GIF, BMP) is centre-cropped to a square, scaled to
 * {@value #SIZE}px and re-encoded as JPEG, so the stored picture is small (typically 10–30 KB), always the
 * same shape, and stripped of metadata. The person's {@code avatarVersion} is bumped on every change so the
 * picture URL changes and browsers never show a stale one.
 */
@Service
public class AvatarService {

    public static final int SIZE = 256;
    public static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;
    private static final float JPEG_QUALITY = 0.86f;

    public static class InvalidImage extends RuntimeException {
        public InvalidImage(String message) { super(message); }
    }

    private final PersonAvatarRepository avatars;
    private final PersonRepository people;

    public AvatarService(PersonAvatarRepository avatars, PersonRepository people) {
        this.avatars = avatars;
        this.people = people;
    }

    public Optional<PersonAvatar> find(Person p) {
        return p.isHasAvatar() ? avatars.findById(p.getId()) : Optional.empty();
    }

    @Transactional
    public void store(Person p, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidImage("Choose a picture first.");
        if (file.getSize() > MAX_UPLOAD_BYTES) throw new InvalidImage("That file is over 8 MB — use a smaller picture.");
        byte[] jpeg = normalise(file);
        avatars.findById(p.getId()).ifPresentOrElse(
                a -> a.replace("image/jpeg", jpeg),
                () -> avatars.save(new PersonAvatar(p.getId(), "image/jpeg", jpeg)));
        p.setAvatarVersion(p.getAvatarVersion() + 1);
        people.save(p);
    }

    @Transactional
    public void remove(Person p) {
        avatars.deleteById(p.getId());
        p.setAvatarVersion(0);
        people.save(p);
    }

    /** Decode → square crop → scale → JPEG. Package-private for tests. */
    static byte[] normalise(MultipartFile file) {
        BufferedImage src;
        try {
            src = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            throw new InvalidImage("Couldn't read that file as an image.");
        }
        if (src == null) throw new InvalidImage("That doesn't look like a JPEG or PNG picture. HEIC photos from a phone need to be exported as JPEG first.");
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2, y = (src.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, SIZE, SIZE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Two-step scale for large photos keeps it sharp without a full mipmap chain.
        if (side > SIZE * 4) {
            int mid = SIZE * 2;
            BufferedImage tmp = new BufferedImage(mid, mid, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, mid, mid, x, y, x + side, y + side, Color.WHITE, null);
            g2.dispose();
            g.drawImage(tmp, 0, 0, SIZE, SIZE, null);
        } else {
            g.drawImage(src, 0, 0, SIZE, SIZE, x, y, x + side, y + side, Color.WHITE, null);
        }
        g.dispose();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(32 * 1024);
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new InvalidImage("Couldn't process that picture.");
        }
    }
}
