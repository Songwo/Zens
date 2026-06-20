package main

import (
	"encoding/hex"
	"strings"
	"testing"
)

func TestDeterministicRandomStreamIsReproducible(t *testing.T) {
	seed, _ := hex.DecodeString("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff")
	left := newDeterministicRandomStream(seed, "participants-a")
	right := newDeterministicRandomStream(seed, "participants-a")

	for i := 0; i < 32; i++ {
		l, err := left.Intn(17)
		if err != nil {
			t.Fatalf("left.Intn failed: %v", err)
		}
		r, err := right.Intn(17)
		if err != nil {
			t.Fatalf("right.Intn failed: %v", err)
		}
		if l != r {
			t.Fatalf("stream mismatch at %d: %d != %d", i, l, r)
		}
	}
}

func TestParticipantSnapshotHashChangesWithEligibleList(t *testing.T) {
	base := []LotteryParticipant{
		{ID: "u-1", Username: "alice", DisplayName: "Alice", Floor: 2, RepliedAt: "2026-06-18T12:00:00Z"},
		{ID: "u-2", Username: "bob", DisplayName: "Bob", Floor: 3, RepliedAt: "2026-06-18T12:01:00Z"},
	}
	changed := append([]LotteryParticipant(nil), base...)
	changed[1].Floor = 4

	if participantSnapshotHash(base) == participantSnapshotHash(changed) {
		t.Fatal("participant snapshot hash did not change after eligible list changed")
	}
}

func TestSecurePickLotteryUsesStrongSeedAndNoDuplicateWinners(t *testing.T) {
	participants := []LotteryParticipant{
		{ID: "u-1", Username: "alice", DisplayName: "Alice", Floor: 2, RepliedAt: "2026-06-18T12:00:00Z"},
		{ID: "u-2", Username: "bob", DisplayName: "Bob", Floor: 3, RepliedAt: "2026-06-18T12:01:00Z"},
		{ID: "u-3", Username: "chen", DisplayName: "Chen", Floor: 4, RepliedAt: "2026-06-18T12:02:00Z"},
		{ID: "u-4", Username: "dana", DisplayName: "Dana", Floor: 5, RepliedAt: "2026-06-18T12:03:00Z"},
	}

	winners, seed, proof, err := securePickLottery(participants, 3)
	if err != nil {
		t.Fatalf("securePickLottery failed: %v", err)
	}
	if !strings.HasPrefix(seed, "zens-") || len(strings.TrimPrefix(seed, "zens-")) != lotterySeedSize*2 {
		t.Fatalf("seed is not a %d-byte hex value: %q", lotterySeedSize, seed)
	}
	if len(proof) != 64 {
		t.Fatalf("proof should be a sha256 hex digest, got %q", proof)
	}
	if len(winners) != 3 {
		t.Fatalf("expected 3 winners, got %d", len(winners))
	}
	seen := map[string]bool{}
	for _, winner := range winners {
		if seen[winner.ID] {
			t.Fatalf("duplicate winner selected: %s", winner.ID)
		}
		seen[winner.ID] = true
	}
}
