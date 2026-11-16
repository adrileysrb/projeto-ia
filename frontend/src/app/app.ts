import { httpResource } from '@angular/common/http';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

interface HelloResponse {
  message: string;
}

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly hello = httpResource<HelloResponse>(() => '/api/hello');
}
