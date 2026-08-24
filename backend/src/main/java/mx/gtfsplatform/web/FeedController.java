package mx.gtfsplatform.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.AppUser;
import mx.gtfsplatform.domain.Feed;
import mx.gtfsplatform.repository.FeedRepository;
import mx.gtfsplatform.security.CurrentUser;
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

    // Cada usuario administra sus propios feeds (equivalente a los "proyectos" de
    // Conveyal datatools-server); un ADMIN ve todos.
    @GetMapping
    public List<Feed> list() {
        AppUser user = CurrentUser.get();
        return CurrentUser.isAdmin() ? feedRepository.findAll() : feedRepository.findByCreatedBy_Id(user.getId());
    }

    @GetMapping("/{id}")
    public Feed get(@PathVariable UUID id) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + id));
        requireOwnerOrAdmin(feed);
        return feed;
    }

    @PostMapping
    public Feed create(@RequestBody Feed feed) {
        AppUser user = CurrentUser.get();
        feed.setId(null);
        OffsetDateTime now = OffsetDateTime.now();
        feed.setCreatedAt(now);
        feed.setUpdatedAt(now);
        feed.setCreatedBy(user);
        feed.setUpdatedBy(user);
        return feedRepository.save(feed);
    }

    @PutMapping("/{id}")
    public Feed update(@PathVariable UUID id, @RequestBody Feed update) {
        Feed existing = feedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + id));
        requireOwnerOrAdmin(existing);
        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(CurrentUser.get());
        return feedRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        Feed existing = feedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + id));
        requireOwnerOrAdmin(existing);
        feedRepository.deleteById(id);
    }

    private void requireOwnerOrAdmin(Feed feed) {
        AppUser user = CurrentUser.get();
        boolean isOwner = feed.getCreatedBy() != null && feed.getCreatedBy().getId().equals(user.getId());
        if (!isOwner && !CurrentUser.isAdmin()) {
            throw new ForbiddenException("No tienes permiso sobre este feed");
        }
    }
}
