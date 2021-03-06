package games.strategy.triplea.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import games.strategy.engine.data.Route;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.TripleAUnit;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.util.IntegerMap;
import games.strategy.util.Match;


public class TransportUtils {

  /**
   * Returns a map of unit -> transport.
   */
  public static Map<Unit, Unit> mapTransports(final Route route, final Collection<Unit> units,
      final Collection<Unit> transportsToLoad) {
    if (route.isLoad()) {
      return mapTransportsToLoad(units, transportsToLoad);
    }
    if (route.isUnload()) {
      return mapTransportsAlreadyLoaded(units, route.getStart().getUnits().getUnits());
    }
    return mapTransportsAlreadyLoaded(units, units);
  }

  /**
   * Returns a map of unit -> transport. Tries to load units evenly across all transports.
   */
  public static Map<Unit, Unit> mapTransportsToLoad(final Collection<Unit> units,
      final Collection<Unit> transports) {

    final List<Unit> canBeTransported = sortByTransportCostDescending(units);
    final List<Unit> canTransport = sortByTransportCapacityDescendingThenMovesDescending(transports);

    // Add units to transports evenly
    final Map<Unit, Unit> mapping = new HashMap<>();
    final IntegerMap<Unit> addedLoad = new IntegerMap<>();
    for (final Unit unit : canBeTransported) {
      final Optional<Unit> transport = loadUnitIntoFirstAvailableTransport(unit, canTransport, mapping, addedLoad);

      // Move loaded transport to end of list
      if (transport.isPresent()) {
        canTransport.remove(transport.get());
        canTransport.add(transport.get());
      }
    }
    return mapping;
  }

  /**
   * Returns a map of unit -> transport. Tries load max units into each transport before moving to next.
   */
  public static Map<Unit, Unit> mapTransportsToLoadUsingMinTransports(final Collection<Unit> units,
      final Collection<Unit> transports) {

    final List<Unit> canBeTransported = sortByTransportCostDescending(units);
    final List<Unit> canTransport = sortByTransportCapacityDescendingThenMovesDescending(transports);

    final Map<Unit, Unit> mapping = new HashMap<>();
    Optional<Unit> finalTransport = Optional.empty();
    for (final Unit currentTransport : canTransport) {

      // Check if remaining units can all be loaded into 1 transport
      final int capacity = TransportTracker.getAvailableCapacity(currentTransport);
      final int remainingCost = getTransportCost(canBeTransported);
      if (remainingCost <= capacity) {
        if (!finalTransport.isPresent() || capacity < TransportTracker.getAvailableCapacity(finalTransport.get())) {
          finalTransport = Optional.of(currentTransport);
        }
        continue; // Check all transports to find the one with the least remaining capacity that can fit all units
      }

      // Check if we've found the final transport to load remaining units
      if (finalTransport.isPresent()) {
        break;
      }

      loadMaxUnits(currentTransport, canBeTransported, mapping);
    }

    // Load remaining units in final transport
    if (finalTransport.isPresent()) {
      loadMaxUnits(finalTransport.get(), canBeTransported, mapping);
    }

    return mapping;
  }

  /**
   * Returns a map of unit -> transport. Unit must already be loaded in the transport.
   */
  public static Map<Unit, Unit> mapTransportsAlreadyLoaded(final Collection<Unit> units,
      final Collection<Unit> transports) {

    final Collection<Unit> canBeTransported = Match.getMatches(units, Matches.UnitCanBeTransported);
    final Collection<Unit> canTransport = Match.getMatches(transports, Matches.UnitCanTransport);

    final Map<Unit, Unit> mapping = new HashMap<>();
    for (final Unit currentTransported : canBeTransported) {
      final Unit transport = TransportTracker.transportedBy(currentTransported);

      // Already being transported, make sure it is in transports
      if (transport == null || !canTransport.contains(transport)) {
        continue;
      }

      mapping.put(currentTransported, transport);
    }
    return mapping;
  }

  /**
   * Returns list of transports. Transports must contain all units. Can swap units with equivalent state in order to
   * minimize transports used to unload.
   */
  public static Set<Unit> findMinTransportsToUnload(final Collection<Unit> units, final Collection<Unit> transports) {
    final Set<Unit> result = new HashSet<>();
    Map<Unit, List<Unit>> unitToPotentialTransports = findTransportsThatUnitsCouldUnloadFrom(units, transports);
    while (!unitToPotentialTransports.isEmpty()) {
      unitToPotentialTransports = sortByTransportOptionsAscending(unitToPotentialTransports);
      final Unit currentUnit = unitToPotentialTransports.keySet().iterator().next();
      final Unit selectedTransport = findOptimalTransportToUnloadFrom(currentUnit, unitToPotentialTransports);
      unitToPotentialTransports = removeTransportAndLoadedUnits(selectedTransport, unitToPotentialTransports);
      result.add(selectedTransport);
    }
    return result;
  }

  public static List<Unit> findUnitsToLoadOnAirTransports(final Collection<Unit> units,
      final Collection<Unit> transports) {

    final Collection<Unit> airTransports = Match.getMatches(transports, Matches.UnitIsAirTransport);
    final List<Unit> canBeTransported = sortByTransportCostDescending(units);

    // Define the max of all units that could be loaded
    final List<Unit> totalLoad = new ArrayList<>();

    // Get a list of the unit categories
    final Collection<UnitCategory> unitTypes = UnitSeperator.categorize(canBeTransported, null, false, true);
    final Collection<UnitCategory> transportTypes = UnitSeperator.categorize(airTransports, null, false, false);
    for (final UnitCategory unitType : unitTypes) {
      final int transportCost = unitType.getTransportCost();
      for (final UnitCategory transportType : transportTypes) {
        final int transportCapacity = UnitAttachment.get(transportType.getType()).getTransportCapacity();
        if (transportCost > 0 && transportCapacity >= transportCost) {
          final int transportCount = Match.countMatches(airTransports, Matches.unitIsOfType(transportType.getType()));
          final int ttlTransportCapacity = transportCount * (int) Math.floor(transportCapacity / transportCost);
          totalLoad.addAll(Match.getNMatches(canBeTransported, ttlTransportCapacity,
              Matches.unitIsOfType(unitType.getType())));
        }
      }
    }
    return totalLoad;
  }

  public static int getTransportCost(final Collection<Unit> units) {
    if (units == null) {
      return 0;
    }
    int cost = 0;
    final Iterator<Unit> iter = units.iterator();
    while (iter.hasNext()) {
      final Unit item = iter.next();
      cost += UnitAttachment.get(item.getType()).getTransportCost();
    }
    return cost;
  }

  private static List<Unit> sortByTransportCapacityDescendingThenMovesDescending(final Collection<Unit> transports) {
    final Comparator<Unit> transportCapacityComparator = (o1, o2) -> {
      final int capacityLeft1 = TransportTracker.getAvailableCapacity(o1);
      final int capacityLeft2 = TransportTracker.getAvailableCapacity(o2);
      if (capacityLeft1 != capacityLeft2) {
        return Integer.compare(capacityLeft2, capacityLeft1);
      }
      final int movementLeft1 = TripleAUnit.get(o1).getMovementLeft();
      final int movementLeft2 = TripleAUnit.get(o2).getMovementLeft();
      return Integer.compare(movementLeft2, movementLeft1);
    };
    final List<Unit> canTransport = Match.getMatches(transports, Matches.UnitCanTransport);
    Collections.sort(canTransport, transportCapacityComparator);
    return canTransport;
  }

  private static List<Unit> sortByTransportCostDescending(final Collection<Unit> units) {
    final Comparator<Unit> transportCostComparator = (o1, o2) -> {
      final int cost1 = UnitAttachment.get((o1).getUnitType()).getTransportCost();
      final int cost2 = UnitAttachment.get((o2).getUnitType()).getTransportCost();
      return Integer.compare(cost2, cost1);
    };
    final List<Unit> canBeTransported = Match.getMatches(units, Matches.UnitCanBeTransported);
    Collections.sort(canBeTransported, transportCostComparator);
    return canBeTransported;
  }

  private static Optional<Unit> loadUnitIntoFirstAvailableTransport(final Unit unit, final List<Unit> canTransport,
      final Map<Unit, Unit> mapping, final IntegerMap<Unit> addedLoad) {
    final int cost = UnitAttachment.get((unit).getType()).getTransportCost();
    for (final Unit transport : canTransport) {
      final int capacity = TransportTracker.getAvailableCapacity(transport) - addedLoad.getInt(transport);
      if (capacity >= cost) {
        addedLoad.add(transport, cost);
        mapping.put(unit, transport);
        return Optional.of(transport);
      }
    }
    return Optional.empty();
  }

  private static void loadMaxUnits(final Unit transport, final List<Unit> canBeTransported,
      final Map<Unit, Unit> mapping) {
    int capacity = TransportTracker.getAvailableCapacity(transport);
    for (final Iterator<Unit> it = canBeTransported.iterator(); it.hasNext();) {
      final Unit unit = it.next();
      final int cost = UnitAttachment.get((unit).getType()).getTransportCost();
      if (capacity >= cost) {
        capacity -= cost;
        mapping.put(unit, transport);
        it.remove();
      }
    }
  }

  private static Map<Unit, List<Unit>> findTransportsThatUnitsCouldUnloadFrom(final Collection<Unit> units,
      final Collection<Unit> transports) {
    final List<Unit> canBeTransported = Match.getMatches(units, Matches.UnitCanBeTransported);
    final List<Unit> canTransport = Match.getMatches(transports, Matches.UnitCanTransport);
    final Map<Unit, List<Unit>> result = new LinkedHashMap<>();
    for (final Unit unit : canBeTransported) {
      final List<Unit> transportOptions = new ArrayList<>();
      for (final Unit transport : canTransport) {
        if (containsEquivalentUnit(unit, TransportTracker.transporting(transport))) {
          transportOptions.add(transport);
        }
      }
      result.put(unit, transportOptions);
    }
    return result;
  }

  private static Map<Unit, List<Unit>> sortByTransportOptionsAscending(
      final Map<Unit, List<Unit>> unitToPotentialTransports) {
    final Map<Unit, List<Unit>> result = new LinkedHashMap<>();
    unitToPotentialTransports.entrySet().stream()
        .sorted((o1, o2) -> o1.getValue().size() - o2.getValue().size())
        .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
    return result;
  }

  private static Unit findOptimalTransportToUnloadFrom(final Unit unit,
      final Map<Unit, List<Unit>> unitToPotentialTransports) {
    double minAverageTransportOptions = Integer.MAX_VALUE;
    Unit selectedTransport = unitToPotentialTransports.get(unit).get(0);
    for (final Unit transport : unitToPotentialTransports.get(unit)) {
      int transportOptions = 0;
      for (final Unit loadedUnit : TransportTracker.transporting(transport)) {
        if (containsEquivalentUnit(loadedUnit, unitToPotentialTransports.keySet())) {
          final Unit equivalentUnit = getEquivalentUnit(loadedUnit, unitToPotentialTransports.keySet());
          transportOptions += unitToPotentialTransports.get(equivalentUnit).size();
        } else {
          transportOptions = Integer.MAX_VALUE;
          break;
        }
      }
      final double averageTransportOptions =
          (double) transportOptions / TransportTracker.transporting(transport).size();
      if (averageTransportOptions < minAverageTransportOptions) {
        minAverageTransportOptions = averageTransportOptions;
        selectedTransport = transport;
      }
    }
    return selectedTransport;
  }

  private static Map<Unit, List<Unit>> removeTransportAndLoadedUnits(final Unit transport,
      final Map<Unit, List<Unit>> unitToPotentialTransports) {
    for (final Unit loadedUnit : TransportTracker.transporting(transport)) {
      if (containsEquivalentUnit(loadedUnit, unitToPotentialTransports.keySet())) {
        final Unit unit = getEquivalentUnit(loadedUnit, unitToPotentialTransports.keySet());
        unitToPotentialTransports.remove(unit);
      }
    }
    unitToPotentialTransports.values().stream().forEach(t -> t.remove(transport));
    return unitToPotentialTransports;
  }

  private static boolean containsEquivalentUnit(final Unit unit, final Collection<Unit> units) {
    return units.stream().anyMatch(u -> u.isEquivalent(unit));
  }

  private static Unit getEquivalentUnit(final Unit unit, final Collection<Unit> units) {
    return units.stream().filter(u -> u.isEquivalent(unit)).findFirst().get();
  }

}
