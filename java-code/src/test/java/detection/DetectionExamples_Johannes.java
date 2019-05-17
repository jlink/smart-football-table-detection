package detection;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

import detection.data.Table;
import detection.data.position.*;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.*;
import net.jqwik.api.arbitraries.*;

import static java.util.concurrent.TimeUnit.*;

import static net.jqwik.api.Arbitraries.*;
import static net.jqwik.api.Combinators.*;

class DetectionExamples_Johannes {

	@Property(tries = 10)
	//@Report(Reporting.GENERATED)
	void gameSituations(
			@ForAll("gameSituations") final List<RelativePosition> positions
	) {
		long length = positions.get(positions.size() - 1).getTimestamp() - positions.get(0).getTimestamp();
		System.out.println("situation length: " + length);
	}

	@Provide
	Arbitrary<List<RelativePosition>> gameSituations() {

		Arbitrary<Long> frequency = Arbitraries.longs().between(10L, 100L);

		return frequency.flatMap(
				f -> gameSequence()
							 .withSamplingFrequency(Arbitraries.longs().between(f - 2, f + 2))
							 .addSequence(kickoffPosition().forDuration(Tuple.of(SECONDS, 1L)))
							 .addSequence(offTablePosition().forDuration(Tuple.of(SECONDS, 2L)))
							 .build()
		);

//		return gameSituation()
//					   .withSamplingFrequency(MILLISECONDS, longs().between(5, 1000))
//					   .sequence(kickoff().forPeriod(SECONDS, integers().greaterOrEqual(2)))
//					   .sequence(frontOfLeftGoal())
//					   .sequence(offTable().forPeriod(SECONDS, integers().greaterOrEqual(2)))
//					   .build();
	}

	private PositionSequenceBuilder kickoffPosition() {
		Arbitrary<Function<Long, RelativePosition>> kickoff =
				Combinators.combine(middleLine(), wholeTable())
						   .as((x, y) -> ts -> RelativePosition.create(ts, x, y));
		return new PositionSequenceBuilder(kickoff);
	}

	private static DoubleArbitrary middleLine() {
		return doubles().between(0.45, 0.55);
	}

	private static DoubleArbitrary wholeTable() {
		return doubles().between(0, 1);
	}

	private PositionSequenceBuilder offTablePosition() {
		return new PositionSequenceBuilder(Arbitraries.constant(RelativePosition::noPosition));
	}

	private GameSequenceBuilder gameSequence() {
		return new GameSequenceBuilder();
	}

	@Provide
	Arbitrary<Table> table() {
		return combine( //
						integers().greaterOrEqual(1), //
						integers().greaterOrEqual(1)
		) //
		  .as((width, height) -> new Table(width, height));
	}

//	@Provide
//	Arbitrary<List<RelativePosition>> positionsOnTable() {
//		return arbitrary(ts -> anywhereOnTable(ts).elementsMin(2).add().build());
//	}
//
//	@Provide
//	Arbitrary<List<RelativePosition>> goalSituationsLeft() {
//		return arbitrary(ts -> kickoff(ts).scoreLeft().ballNotInCorner().build());
//	}
//
//	@Provide
//	Arbitrary<List<RelativePosition>> goalSituationsRight() {
//		return arbitrary(ts -> kickoff(ts).scoreRight().ballNotInCorner().build());
//	}
//
//	@Provide
//	Arbitrary<List<RelativePosition>> leftGoalsToReverse() {
//		return arbitrary(ts -> kickoff(ts).scoreLeft().ballInCorner().build());
//	}
//
//	@Provide
//	Arbitrary<List<RelativePosition>> rightGoalsToReverse() {
//		return arbitrary(ts -> kickoff(ts).scoreRight().ballInCorner().build());
//	}
//
//	private Arbitrary<List<RelativePosition>> arbitrary(
//			final Function<AtomicLong, Arbitrary<List<RelativePosition>>> mapper
//	) {
//		return longs().map(AtomicLong::new).flatMap(mapper);
//	}

	@FunctionalInterface
	interface PositionCreatorArbitrary extends Arbitrary<Function<Long, RelativePosition>> {
	}

	static class GameSequenceBuilder {

		private Arbitrary<Long> samplingFrequency = Arbitraries.constant(10L);
		private List<PositionSequenceBuilder> sequences = new ArrayList<>();

		public GameSequenceBuilder withSamplingFrequency(long samplingFrequencyMillis) {
			return withSamplingFrequency(Arbitraries.constant(samplingFrequencyMillis));
		}

		public GameSequenceBuilder withSamplingFrequency(Arbitrary<Long> samplingFrequency) {
			this.samplingFrequency = samplingFrequency;
			return this;
		}

		public GameSequenceBuilder addSequence(PositionSequenceBuilder positionSequence) {
			sequences.add(positionSequence);
			return this;
		}

		public Arbitrary<List<RelativePosition>> build() {
			long initialTimestamp = 0L;

			List<Arbitrary<List<Tuple2<Long, Function<Long, RelativePosition>>>>> arbitraries =
					sequences
							.stream()
							.map(sequence -> sequence.build(samplingFrequency))
							.collect(Collectors.toList());

			return Combinators.combine(arbitraries).as(tuplesLists -> {
				List<RelativePosition> all = new ArrayList<>();
				AtomicLong timestamp = new AtomicLong(initialTimestamp);
				for (List<Tuple2<Long, Function<Long, RelativePosition>>> tupleList : tuplesLists) {
					List<RelativePosition> positions =
							tupleList.stream()
									 .map(tuple -> {
										 long ts = timestamp.getAndAdd(tuple.get1());
										 return tuple.get2().apply(ts);
									 })
									 .collect(Collectors.toList());
					all.addAll(positions);
				}
				return all;
			});
		}

//		public GameSituationBuilder kickoff() {
//			return add(kickoffPositions(timestamp)).anywhereOnTableBuilder().add();
//		}
//
//		private static boolean isCorner(final RelativePosition pos) {
//			return 0.5 + abs(0.5 - pos.getX()) >= 0.99 && 0.5 + abs(0.5 - pos.getY()) >= 0.99;
//		}
//
//		private static Arbitrary<RelativePosition> offTablePosition(final AtomicLong timestamp) {
//			return diffInMillis().map(millis -> noPosition(timestamp.addAndGet(millis)));
//		}
//
//		private static LongArbitrary diffInMillis() {
//			return longs().between(MILLISECONDS.toMillis(1), SECONDS.toMillis(10));
//		}
//
//		private Sizeable anywhereOnTableBuilder() {
//			return new Sizeable(combine(diffInMillis(), wholeTable(), wholeTable()) //
//					.as((millis, x, y) //
//					-> create(timestamp.addAndGet(millis), x, y)));
//		}
//
//		public GameSituationBuilder scoreLeft() {
//			return add(prepareLeftGoal(timestamp)).add(offTablePositions(timestamp));
//		}
//
//		public GameSituationBuilder scoreRight() {
//			return add(prepareRightGoal(timestamp)).add(offTablePositions(timestamp));
//		}
//
//		private static Arbitrary<List<RelativePosition>> offTablePositions(final AtomicLong timestamp) {
//			// TODO create as many positions as needed (2000ms between first and last)
//			return offTablePosition(timestamp).list().ofSize(10);
//		}
//
//		public GameSituationBuilder ballNotInCorner() {
//			arbitraries.add(notCorner(timestamp));
//			return this;
//
//		}
//
//		public GameSituationBuilder ballInCorner() {
//			arbitraries.add(corner(timestamp));
//			return this;
//
//		}
//
//		public GameSituationBuilder add(final Arbitrary<List<RelativePosition>> arbitrary) {
//			arbitraries.add(arbitrary);
//			return this;
//		}
//
//		private static Arbitrary<List<RelativePosition>> kickoffPositions(final AtomicLong timestamp) {
//			return atMiddleLine(timestamp).list().ofMinSize(1);
//		}
//
//		private static Arbitrary<List<RelativePosition>> prepareLeftGoal(final AtomicLong timestamp) {
//			return frontOfLeftGoal(timestamp).list().ofMinSize(1);
//		}
//
//		private static Arbitrary<List<RelativePosition>> prepareRightGoal(final AtomicLong timestamp) {
//			return frontOfRightGoal(timestamp).list().ofMinSize(1);
//		}
//
//		private static Arbitrary<RelativePosition> frontOfLeftGoal(final AtomicLong timestamp) {
//			return combine(diffInMillis(), frontOfLeftGoal(), wholeTable()) //
//					.as((millis, x, y) //
//					-> create(timestamp.addAndGet(millis), x, y));
//		}
//
//		private static Arbitrary<RelativePosition> frontOfRightGoal(final AtomicLong timestamp) {
//			return combine(diffInMillis(), frontOfRightGoal(), wholeTable()) //
//					.as((millis, x, y) //
//					-> create(timestamp.addAndGet(millis), x, y));
//		}
//
//		private static Arbitrary<RelativePosition> atMiddleLine(final AtomicLong timestamp) {
//			return combine(diffInMillis(), middleLine(), wholeTable()) //
//					.as((millis, x, y) //
//					-> create(timestamp.addAndGet(millis), x, y));
//		}
//
//		private static Arbitrary<List<RelativePosition>> notCorner(final AtomicLong timestamp) {
//			return combine(diffInMillis(), wholeTable(), wholeTable()) //
//					.as((millis, x, y) //
//					-> create(timestamp.addAndGet(millis), x, y)).list().ofMinSize(1)
//					.filter(c -> c.isEmpty() || !isCorner(c.get(0)));
//		}
//
//		private static Arbitrary<List<RelativePosition>> corner(final AtomicLong timestamp) {
//			return combine(diffInMillis(), corner(), corner(), bool(), bool()) //
//					.as((millis, x, y, swapX, swapY) //
//					-> create(timestamp.addAndGet(millis), possiblySwap(x, swapX), possiblySwap(y, swapY))).list()
//					.ofMinSize(1);
//		}
//
//		private static double possiblySwap(final double value, final boolean swap) {
//			return swap ? 1.00 - value : value;
//		}
//
//		private static Arbitrary<Double> corner() {
//			return doubles().between(0.99, 1.00);
//		}
//
//		private static DoubleArbitrary wholeTable() {
//			return doubles().between(0, 1);
//		}
//
//		private static DoubleArbitrary middleLine() {
//			return doubles().between(0.45, 0.55);
//		}
//
//		private static DoubleArbitrary frontOfLeftGoal() {
//			return doubles().between(0, 0.3);
//		}
//
//		private static DoubleArbitrary frontOfRightGoal() {
//			return doubles().between(0.7, 1);
//		}
//
//		private static Arbitrary<Boolean> bool() {
//			return Arbitraries.of(true, false);
//		}

	}

	static class PositionSequenceBuilder {

		private final Arbitrary<Function<Long, RelativePosition>> positionCreatorArbitrary;
		private Tuple2<TimeUnit, Long> duration;

		public PositionSequenceBuilder(Arbitrary<Function<Long, RelativePosition>> positionCreatorArbitrary) {
			this.positionCreatorArbitrary = positionCreatorArbitrary;
		}

		public PositionSequenceBuilder forDuration(Tuple2<TimeUnit, Long> duration) {
			this.duration = duration;
			return this;
		}

		public Arbitrary<List<Tuple2<Long, Function<Long, RelativePosition>>>> build(Arbitrary<Long> frequencyArbitrary) {
			long durationMillis = duration.get1().toMillis(duration.get2());
			Arbitrary<List<Long>> timestamps = arbitraryCollect(
					frequencyArbitrary,
					base -> durationReached(base, durationMillis)
			);

			return timestamps.flatMap(
					stamps -> {
						SizableArbitrary<List<Function<Long, RelativePosition>>> positionCreators =
								positionCreatorArbitrary.list().ofSize(stamps.size());
						return positionCreators
									   .map(creators -> {
										   List<Tuple2<Long, Function<Long, RelativePosition>>> tuples = new ArrayList<>();
										   for (int i = 0; i < stamps.size(); i++) {
											   tuples.add(Tuple.of(stamps.get(i), creators.get(i)));
										   }
										   return tuples;
									   });
					});

		}

		private boolean durationReached(List<Long> timestamps, long minDuration) {
			if (timestamps.isEmpty()) {
				return false;
			}
			long lastTimestamp = timestamps.get(timestamps.size() - 1);
			long duration = timestamps.stream().mapToLong(l -> l).sum() - lastTimestamp;
			return duration >= minDuration;
		}

		static <T> Arbitrary<List<T>> arbitraryCollect(
				Arbitrary<T> elementArbitrary,
				Predicate<List<T>> until
		) {
			return new ArbitraryCollect<>(elementArbitrary, until);
		}

	}
}
