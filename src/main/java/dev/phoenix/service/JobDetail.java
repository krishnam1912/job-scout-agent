package dev.phoenix.service;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Event;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.Score;

import java.util.List;

/** Everything the engine knows about one job; {@code score} and {@code application} are null when absent. */
public record JobDetail(Job job, Score score, Application application, List<Event> events, List<Job> groupMembers) {
}
