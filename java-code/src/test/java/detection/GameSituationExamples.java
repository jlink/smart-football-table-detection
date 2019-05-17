package detection;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

import detection.data.Table;
import detection.data.position.*;
import org.junit.jupiter.api.*;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.*;
import net.jqwik.api.arbitraries.*;

import static java.util.concurrent.TimeUnit.*;

import static net.jqwik.api.Arbitraries.*;
import static net.jqwik.api.Combinators.*;

class GameSituationExamples {

	@Property(tries = 10)
	//	@Report(Reporting.GENERATED)
	void goalSituations(@ForAll("goalSituations") final List<RelativePosition> situation) {
		assertDuration(situation, 4000, 4500);
	}

	private void assertDuration(@ForAll("goalSituations") List<RelativePosition> positions, int minDurationMillis, int maxDurationMillis) {
		long duration = positions.get(positions.size() - 1).getTimestamp() - positions.get(0).getTimestamp();
		Assertions.assertTrue(duration >= minDurationMillis,
							  String.format("duration should be at least %s ms but is %s", minDurationMillis, duration));
		Assertions.assertTrue(duration <= maxDurationMillis,
							  String.format("duration should be at most %s ms but is %s", maxDurationMillis, duration));
		// System.out.println("situation length [ms]: " + duration);
	}

	@Provide
	Arbitrary<List<RelativePosition>> goalSituations() {

		Arbitrary<Long> sampleFrequencyMillis = Arbitraries.longs().between(10L, 100L);
		Arbitrary<Long> startingTimestamp = longs().between(0, Long.MAX_VALUE / 2);

		return sampleFrequencyMillis.flatMap(
				frequency -> gameSequence()
									 .withSamplingFrequency(Arbitraries.longs().between(frequency - 2, frequency + 2))
									 .addSequence(kickoffPosition().forDuration(Tuple.of(SECONDS, 1L)))
									 .addSequence(frontOfLeftGoalPosition()) // Default duration 1 sec
									 .addSequence(offTablePosition().forDuration(Tuple.of(SECONDS, 2L)))
									 .build(startingTimestamp)
		);

	}

	private PositionSequenceBuilder kickoffPosition() {
		return position(middleLine(), wholeTable());
	}

	private PositionSequenceBuilder frontOfLeftGoalPosition() {
		return position(frontOfLeftGoal(), wholeTable());
	}

	private PositionSequenceBuilder offTablePosition() {
		return new PositionSequenceBuilder(Arbitraries.constant(RelativePosition::noPosition));
	}

	private PositionSequenceBuilder position(DoubleArbitrary xPosition, DoubleArbitrary yPosition) {
		Arbitrary<Function<Long, RelativePosition>> kickoff =
				Combinators.combine(xPosition, yPosition)
						   .as((x, y) -> ts -> RelativePosition.create(ts, x, y));
		return new PositionSequenceBuilder(kickoff);
	}

	private static DoubleArbitrary middleLine() {
		return doubles().between(0.45, 0.55);
	}

	private static DoubleArbitrary wholeTable() {
		return doubles().between(0, 1);
	}

	private static DoubleArbitrary frontOfLeftGoal() {
		return doubles().between(0, 0.3);
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
			return build(Arbitraries.constant(0L));
		}

		public Arbitrary<List<RelativePosition>> build(Arbitrary<Long> initialTimestampArbitrary) {
			List<Arbitrary<List<Tuple2<Long, Function<Long, RelativePosition>>>>> arbitraries =
					sequences
							.stream()
							.map(sequence -> sequence.build(samplingFrequency))
							.collect(Collectors.toList());

			return initialTimestampArbitrary.flatMap(
					initialTimestamp ->
							Combinators.combine(arbitraries).as(
									tuplesLists -> {
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
									}));
		}

	}

	static class PositionSequenceBuilder {

		public static final Tuple2<TimeUnit, Long> DEFAULT_DURATION = Tuple.of(SECONDS, 1L);

		private final Arbitrary<Function<Long, RelativePosition>> positionCreatorArbitrary;
		private Tuple2<TimeUnit, Long> duration = DEFAULT_DURATION;
		private Arbitrary<Tuple2<TimeUnit, Long>> durationArbitrary = Arbitraries.constant(DEFAULT_DURATION);

		public PositionSequenceBuilder(Arbitrary<Function<Long, RelativePosition>> positionCreatorArbitrary) {
			this.positionCreatorArbitrary = positionCreatorArbitrary;
		}

		public PositionSequenceBuilder forDuration(Tuple2<TimeUnit, Long> duration) {
			this.duration = duration;
			return forDuration(Arbitraries.constant(duration));
		}

		public PositionSequenceBuilder forDuration(Arbitrary<Tuple2<TimeUnit, Long>> durationArbitrary) {
			this.durationArbitrary = durationArbitrary;
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
