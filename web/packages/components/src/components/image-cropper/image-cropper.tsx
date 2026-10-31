import { useEffect, useRef, useState, type SyntheticEvent } from "react";
import ReactCrop, { centerCrop, makeAspectCrop, type Crop, type PixelCrop } from "react-image-crop";
import "react-image-crop/dist/ReactCrop.css";
import { Button } from "../button";
import { Dialog } from "../dialog";
import styles from "./image-cropper.module.css";

export interface ImageCropperResult {
  blob: Blob;
  dataUrl: string;
  crop: PixelCrop;
}

export interface ImageCropperProps {
  open: boolean;
  src: string;
  onClose: () => void;
  onComplete: (result: ImageCropperResult) => void;
  aspect?: number;
  circularCrop?: boolean;
  title?: string;
  cancelText?: string;
  confirmText?: string;
  maxOutputSize?: number;
}

/** Avatar/image cropper using react-image-crop inside a Dialog. */
export function ImageCropper({
  open,
  src,
  onClose,
  onComplete,
  aspect = 1,
  circularCrop = true,
  title = "Crop image",
  cancelText = "Cancel",
  confirmText = "Apply crop",
  maxOutputSize = 512,
}: ImageCropperProps) {
  const imageRef = useRef<HTMLImageElement>(null);
  const [crop, setCrop] = useState<Crop>();
  const [completedCrop, setCompletedCrop] = useState<PixelCrop>();

  useEffect(() => {
    if (!open) {
      setCrop(undefined);
      setCompletedCrop(undefined);
    }
  }, [open, src]);

  const handleImageLoad = (event: SyntheticEvent<HTMLImageElement>): void => {
    const { width, height } = event.currentTarget;
    const initialCrop = centerCrop(
      makeAspectCrop({ unit: "%", width: 80 }, aspect, width, height),
      width,
      height,
    );
    setCrop(initialCrop);
  };

  const handleConfirm = (): void => {
    const image = imageRef.current;
    if (!image || !completedCrop || completedCrop.width === 0) {
      return;
    }

    const scaleX = image.naturalWidth / image.width;
    const scaleY = image.naturalHeight / image.height;
    const cropWidth = completedCrop.width * scaleX;
    const cropHeight = completedCrop.height * scaleY;
    const outputScale = Math.min(1, maxOutputSize / Math.max(cropWidth, cropHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(cropWidth * outputScale));
    canvas.height = Math.max(1, Math.round(cropHeight * outputScale));
    const context = canvas.getContext("2d");
    if (!context) {
      return;
    }

    context.drawImage(
      image,
      completedCrop.x * scaleX,
      completedCrop.y * scaleY,
      cropWidth,
      cropHeight,
      0,
      0,
      canvas.width,
      canvas.height,
    );

    canvas.toBlob((blob) => {
      if (!blob) {
        return;
      }
      onComplete({
        blob,
        dataUrl: canvas.toDataURL("image/png"),
        crop: completedCrop,
      });
      onClose();
    }, "image/png");
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      size="md"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {cancelText}
          </Button>
          <Button onClick={handleConfirm} disabled={!completedCrop?.width}>
            {confirmText}
          </Button>
        </>
      }
    >
      <div className={styles.cropArea}>
        <ReactCrop
          crop={crop}
          aspect={aspect}
          circularCrop={circularCrop}
          onChange={(nextCrop) => setCrop(nextCrop)}
          onComplete={(nextCrop) => setCompletedCrop(nextCrop)}
          className={styles.crop}
          ruleOfThirds
        >
          <img
            ref={imageRef}
            src={src}
            alt="Crop source"
            className={styles.image}
            onLoad={handleImageLoad}
          />
        </ReactCrop>
      </div>
    </Dialog>
  );
}
