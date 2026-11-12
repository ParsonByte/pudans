import {
  useEffect,
  useRef,
  useState,
  type PointerEvent,
  type SyntheticEvent,
} from "react";
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  useMediaQuery,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import ReactCrop, {
  centerCrop,
  makeAspectCrop,
  type Crop,
  type PixelCrop,
} from "react-image-crop";
import "react-image-crop/dist/ReactCrop.css";
import { Icon } from "../icon";

export interface ImageCropperResult {
  blob: Blob;
  dataUrl: string;
  crop: PixelCrop;
}

export interface ImageCropperProps {
  open: boolean;
  src: string;
  onClose: () => void;
  onComplete: (result: ImageCropperResult) => void | Promise<void>;
  aspect?: number;
  circularCrop?: boolean;
  title?: string;
  cancelText?: string;
  confirmText?: string;
  maxOutputSize?: number;
  /** Controlled loading flag (uploading); disables close and confirm. */
  loading?: boolean;
}

function centerAspectCrop(width: number, height: number, aspect: number): Crop {
  return centerCrop(makeAspectCrop({ unit: "px", width: width * 0.9 }, aspect, width, height), width, height);
}

/**
 * Legacy Pages/Setting/AvatarCropperDialog: MUI Dialog (fullWidth, maxWidth xs),
 * DialogTitle with the Dismiss IconButton, the legacy DialogContent padding
 * (paddingTop 0 via theme), a circular ReactCrop area with minWidth/minHeight 100
 * and image maxWidth 100% / maxHeight 60vh, and DialogActions with 16px 24px
 * padding + text Cancel / contained OK (loading spinner while uploading).
 * The paper can be dragged by its header on fine pointers, matching the legacy
 * react-draggable DraggableDialog.
 */
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
  loading = false,
}: ImageCropperProps) {
  const theme = useTheme();
  const coarsePointer = useMediaQuery("(pointer: coarse)");
  const imageRef = useRef<HTMLImageElement>(null);
  const paperRef = useRef<HTMLDivElement | null>(null);
  const dragOrigin = useRef({ pointerX: 0, pointerY: 0, x: 0, y: 0 });
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [crop, setCrop] = useState<Crop>();
  const [completedCrop, setCompletedCrop] = useState<PixelCrop>();
  const [uploading, setUploading] = useState(false);

  const busy = loading || uploading;

  useEffect(() => {
    if (!open) {
      setCrop(undefined);
      setCompletedCrop(undefined);
      setOffset({ x: 0, y: 0 });
      setUploading(false);
    }
  }, [open, src]);

  useEffect(() => {
    if (paperRef.current) {
      paperRef.current.style.transform =
        offset.x === 0 && offset.y === 0 ? "" : `translate(${offset.x}px, ${offset.y}px)`;
    }
  }, [offset]);

  const handleDragStart = (event: PointerEvent<HTMLDivElement>): void => {
    if (coarsePointer || event.button !== 0) {
      return;
    }
    const target = event.target as HTMLElement;
    if (!target.closest("[data-drag-handle]") || target.closest("button, a, input")) {
      return;
    }
    dragOrigin.current = {
      pointerX: event.clientX,
      pointerY: event.clientY,
      x: offset.x,
      y: offset.y,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleDragMove = (event: PointerEvent<HTMLDivElement>): void => {
    if (!event.currentTarget.hasPointerCapture(event.pointerId)) {
      return;
    }
    setOffset({
      x: dragOrigin.current.x + (event.clientX - dragOrigin.current.pointerX),
      y: dragOrigin.current.y + (event.clientY - dragOrigin.current.pointerY),
    });
  };

  const handleDragEnd = (event: PointerEvent<HTMLDivElement>): void => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const handleImageLoad = (event: SyntheticEvent<HTMLImageElement>): void => {
    const { width, height } = event.currentTarget;
    setCrop(centerAspectCrop(width, height, aspect));
  };

  const handleClose = (): void => {
    if (!busy) {
      onClose();
    }
  };

  const handleConfirm = async (): Promise<void> => {
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
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
    if (!blob) {
      return;
    }
    setUploading(true);
    try {
      await onComplete({ blob, dataUrl: canvas.toDataURL("image/png"), crop: completedCrop });
      onClose();
    } finally {
      setUploading(false);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={busy ? undefined : handleClose}
      fullWidth
      maxWidth="xs"
      slotProps={{
        paper: {
          ref: paperRef,
          sx: { cursor: coarsePointer ? "default" : undefined },
          onPointerDown: coarsePointer ? undefined : handleDragStart,
          onPointerMove: coarsePointer ? undefined : handleDragMove,
          onPointerUp: coarsePointer ? undefined : handleDragEnd,
          onPointerCancel: coarsePointer ? undefined : handleDragEnd,
        },
      }}
    >
      <DialogTitle
        data-drag-handle
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          cursor: coarsePointer ? "initial" : "move",
        }}
      >
        <Box component="span" sx={{ userSelect: "none" }}>
          {title}
        </Box>
        <IconButton disabled={busy} onClick={handleClose} aria-label="Close">
          <Icon name="pd:Dismiss" size={20} />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: "flex", justifyContent: "center" }}>
          {src ? (
            <ReactCrop
              circularCrop={circularCrop}
              crop={crop}
              minWidth={100}
              minHeight={100}
              aspect={aspect}
              onChange={(_pixelCrop, percentCrop) => setCrop(percentCrop)}
              onComplete={(pixelCrop) => setCompletedCrop(pixelCrop)}
            >
              <img
                ref={imageRef}
                src={src}
                alt="Avatar"
                onLoad={handleImageLoad}
                style={{ maxWidth: "100%", maxHeight: "60vh", display: "block" }}
              />
            </ReactCrop>
          ) : null}
        </Box>
      </DialogContent>
      <DialogActions
        sx={{
          px: 3,
          py: 2,
          justifyContent: "space-between",
        }}
      >
        <Box />
        <Stack direction="row" spacing={1}>
          <Button disabled={busy} onClick={handleClose}>
            {cancelText}
          </Button>
          <Button
            variant="contained"
            color="primary"
            disabled={busy || !completedCrop?.width}
            onClick={() => {
              void handleConfirm();
            }}
            aria-busy={busy || undefined}
          >
            {busy ? (
              <CircularProgress size={theme.spacing(2)} color="inherit" />
            ) : (
              confirmText
            )}
          </Button>
        </Stack>
      </DialogActions>
    </Dialog>
  );
}
