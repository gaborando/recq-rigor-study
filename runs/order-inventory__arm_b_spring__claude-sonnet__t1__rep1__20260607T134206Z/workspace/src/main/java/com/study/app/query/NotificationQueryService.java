package com.study.app.query;

import com.study.app.domain.CustomerRepository;
import com.study.app.domain.Notification;
import com.study.app.domain.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationQueryService {

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private CustomerRepository customerRepository;

    public List<Notification> getNotifications(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        return notificationRepository.findByCustomerId(customerId);
    }
}
