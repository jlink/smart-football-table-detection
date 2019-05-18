package detection;

import java.util.*;
import java.util.function.*;

import net.jqwik.api.*;

// TODO: No more needed in jqwik >= 1.1.4-SNAPSHOT
// use Arbitrary.collect(until) instead
public class ArbitraryCollect<T> implements Arbitrary<List<T>> {
	private final Arbitrary<T> elementArbitrary;
	private final Predicate<List<T>> until;

	public ArbitraryCollect(
			Arbitrary<T> elementArbitrary,
			Predicate<List<T>> until
	) {
		this.elementArbitrary = elementArbitrary;
		this.until = until;
	}

	@Override
	public RandomGenerator<List<T>> generator(int genSize) {
		final RandomGenerator<T> elementGenerator = elementArbitrary.generator(genSize);
		return random -> {
			List<T> base = new ArrayList<>();
			while (!until.test(base)) {
				Shrinkable<T> shrinkable = elementGenerator.next(random);
				base.add(shrinkable.value());
			}
			return Shrinkable.unshrinkable(base);
		};
	}
}
