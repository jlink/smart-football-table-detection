package detection.detector;

import static java.lang.Math.abs;
import static java.util.concurrent.TimeUnit.SECONDS;

import detection.data.position.AbsolutePosition;
import detection.data.position.RelativePosition;

public class FoulDetector implements Detector {

	public static interface Listener {
		void foulHappenend();
	}

	private static final long TIMEOUT = SECONDS.toMillis(15);
	private static final double MOVEMENT_GREATER_THAN = 0.05;

	public static FoulDetector onFoul(Listener listener) {
		return new FoulDetector(listener);
	}

	@Override
	public FoulDetector newInstance() {
		return new FoulDetector(listener);
	}

	private final FoulDetector.Listener listener;

	private RelativePosition noMovementSince;
	private boolean foulInProgress;

	private FoulDetector(FoulDetector.Listener listener) {
		this.listener = listener;
	}

	@Override
	public void detect(AbsolutePosition pos) {
		if (noMovementSince == null) {
			if (!pos.isNull()) {
				noMovementSince = pos.getRelativePosition();
			}
		} else if (pos.isNull() || xChanged(pos)) {
			noMovementSince = null;
			foulInProgress = false;
		} else if (noMovementDurationInMillis(pos) >= TIMEOUT) {
			if (!foulInProgress) {
				listener.foulHappenend();
				foulInProgress = true;
			}
		}
	}

	private long noMovementDurationInMillis(AbsolutePosition pos) {
		return pos.getTimestamp() - noMovementSince.getTimestamp();
	}

	private boolean xChanged(AbsolutePosition pos) {
		return xDiff(pos) > MOVEMENT_GREATER_THAN;
	}

	private double xDiff(AbsolutePosition pos) {
		return abs(pos.getRelativePosition().getX() - noMovementSince.getX());
	}

}