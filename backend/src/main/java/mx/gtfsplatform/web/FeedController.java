package mx.gtfsplatform.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.Feed;
import mx.gtfsplatform.repository.FeedRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feeds")
public class FeedController {

    private final FeedRepository feedRepository;

    public FeedController(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @GetMapping
    public List<Feed> list() {
        return feedRepository.findAll();
    }

    @GetMapping("/{id}")
    public Feed get(@PathVariable UUID id) {
        return feedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + id));
    }

    @PostMapping
    public Feed create(@RequestBody Feed feed) {
        feed.setId(null);
        OffsetDateTime now = OffsetDateTime.now();
        feed.setCreatedAt(now);
        feed.setUpdatedAt(now);
        return feedRepository.save(feed);
    }

    @PutMapping("/{id}")
    public Feed update(@PathVariable UUID id, @RequestBody Feed update) {
        Feed existing = feedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + id));
        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(update.getUpdatedBy());
        return feedRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        feedRepository.deleteById(id);
    }
}
