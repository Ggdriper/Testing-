package com.example.IziRestApiSpring.service;

import com.example.IziRestApiSpring.entity.Task;
import com.example.IziRestApiSpring.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public List<Task> getAllTasks(Boolean completed) {
        if (completed != null) {
            return taskRepository.findByCompleted(completed);
        }
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    public Task createTask(Task task) {
        return taskRepository.save(task);
    }

    public Optional<Task> updateTask(Long id, Task taskDetails) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    if (taskDetails.getTitle() != null) {
                        existingTask.setTitle(taskDetails.getTitle());
                    }
                    if (taskDetails.getDescription() != null) {
                        existingTask.setDescription(taskDetails.getDescription());
                    }
                    if (taskDetails.getCompleted() != null) {
                        existingTask.setCompleted(taskDetails.getCompleted());
                    }
                    return taskRepository.save(existingTask);
                });
    }

    public boolean deleteTask(Long id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
